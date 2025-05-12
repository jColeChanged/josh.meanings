(ns josh.meanings.initializations.utils
  "Utilities to help with rapid k mean cluster initialization."
  (:refer-clojure
   :exclude
   [get nth assoc get-in merge assoc-in update-in select-keys destructure let fn loop defn defn-])
  (:use [josh.meanings.specs])
  (:require [bigml.sampling.reservoir :as res-sample]
            [clojure.spec.alpha :as s]
            [ham-fisted.reduce :as hamfr]
            [ham-fisted.lazy-noncaching :as hfl]
            [josh.meanings.persistence :as p]
            [josh.meanings.records.clustering-state]
            [taoensso.timbre :as log]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.neanderthal :as ds-nean]
            [tech.v3.datatype.functional :as dfn]
            [uncomplicate.neanderthal
             [random :refer [rand-uniform!]]
             [native :as native]]
            [uncomplicate.neanderthal.vect-math :as vm]
            [ham-fisted.api :as hamf]
            [clojure.core :as c]
            [clj-fast.clojure.core :refer [get nth assoc get-in merge assoc-in update-in select-keys destructure let fn loop defn defn-]])
  (:import [josh.meanings.records.clustering_state KMeansState]))




(set! *warn-on-reflection* true)
(set! *unchecked-math* true)

(def t-dataset :josh.meanings.specs/dataset)
(def t-config  :josh.meanings.specs/configuration)
(def t-points  :josh.meanings.specs/points)


(defn vector->dataset
  "Converts a vector of points to a dataset with col-name column names."
  [data col-names]
  (log/info "Converting centroids vector to dataset.")
  (ds/->dataset (map (partial zipmap col-names) data)))

(s/fdef centroids->dataset :args (s/cat :conf t-config :results t-points) :ret t-dataset)
(defn centroids->dataset
  "Converts a vector of points to a dataset."
  [^KMeansState s results]
  {:pre [(= (count results) (:k s))]
   :post [(= (count results) (ds/row-count %))]}
  (vector->dataset results (.column-names s)))


(defn uniform-sample
  [ds-seq n & options]
  (log/debug "Getting uniform sample of size" n)
  (let [sample #(apply res-sample/sample (ds/rowvecs %) n options)]
    (apply res-sample/merge (map sample ds-seq))))




;; What is a good weighted sampling algorithm for sampling k items from a large collection?
;; 
;; Weighted reservoir sampling was researched by Pavlos Efraimidis and Paul Spirakis and a summary of 
;; their research can be found in Weighted Random Sampling [1][2]. 
;;
;; In pseudocode the algorithm can be described in only one line:
;;
;; heapq.nlargest(k, items, key=lambda item: math.pow(random.random(), 1/weight(item)))
;;
;; [1]: https://stackoverflow.com/questions/17117872/is-there-an-algorithm-for-weighted-reservoir-sampling
;; [2]: http://utopia.duth.gr/~pefraimi/research/data/2007EncOfAlg.pdf

;; Java's PriorityQueue class implements a priority heap.  By default it expects to a min heap so 
;; reversing the comparator produces a max heap."
  
(def reservoir-sampling-max-heap-comparator
  (reify java.util.Comparator
    (compare
     [this item1 item2]
     (compare (get item2 :res-rank)  (get item1 :res-rank)))))


(defn max-heap
  "Returns a max heap."
  ^java.util.PriorityQueue
  [k]
  (new java.util.PriorityQueue k reservoir-sampling-max-heap-comparator))

(defn parallel-max-heap
  "Returns a max heap."
  ^java.util.concurrent.PriorityBlockingQueue
  [k]
  (new java.util.concurrent.PriorityBlockingQueue k reservoir-sampling-max-heap-comparator))


(s/fdef generate-random-buffer :args (s/cat :dataset t-dataset))
(defn generate-random-buffer
  "Generates a random buffer."
  [dataset]
  (let [row-count (ds/row-count dataset)
        buffer    (native/fge row-count 1)]
    (rand-uniform! 0.0 1.0 buffer)))


;; Experiments to run:
;; 
;; These tests might not be appropriate because it isn't where the 
;; profiling is showing issues.
;; 
;; 7. Test version which uses random column provided by tech.ml.dataset.
;; 8. Test version which does full res-rank calculation in neanderthal.
(defn reservoir-rank
  [dataset column-name]
  (let [rand-dataset (ds/rename-columns (ds-nean/dense->dataset (generate-random-buffer dataset)) [:random])]
    (assoc dataset :res-rank (dfn/pow (rand-dataset :random) (dfn// 1 (dataset column-name))))))


(defn weighted-sample
  [ds-seq weight-col ^long k]
  (let [add-to-queue  (fn ^java.util.concurrent.PriorityBlockingQueue
                        [^java.util.concurrent.PriorityBlockingQueue acc ds]
                        (doseq [row (ds/rows (if (< (.size acc) k)
                                               ds
                                               (let [lowest (get (.peek acc) :res-rank)]
                                                 (ds/filter-column ds :res-rank (fn [x] (>= x lowest))))))]
                          (if (< (.size acc) k)
                            (.add acc row)
                            (when (< (get (.peek acc) :res-rank) (get row :res-rank))
                              (.poll acc)
                              (.add acc row))))
                        acc)
        merge-queues (fn ^java.util.concurrent.PriorityBlockingQueue
                       [^java.util.concurrent.PriorityBlockingQueue acc
                        ^java.util.concurrent.PriorityBlockingQueue rows]
                       (doseq [row rows]
                         (when (< (get (.peek acc) :res-rank) (get row :res-rank))
                           (.poll acc)
                           (.add acc row)))
                       (while (> (.size acc) k)
                         (.poll acc))
                       acc)
        ^java.util.concurrent.PriorityBlockingQueue q
        (->> ds-seq
             (hfl/map (fn [dataset] (reservoir-rank dataset weight-col)))
             (hamfr/preduce (partial parallel-max-heap k) add-to-queue merge-queues))]
    (->> q
         (.iterator)
         (iterator-seq)
         (into []))))



(s/fdef sample-one :args (s/cat :conf t-config) :ret t-dataset)
(defn sample-one
  "Returns a single item from a near uniform sample of the points dataset in conf.
   In practice this sample is slightly biased towards elements in the final sequences 
   as for the sake of speed we skip weighting based on row count."
  [conf]
  (ds/->dataset [(rand-nth (into [] (hamf/pmap ds/rand-nth (p/read-dataset-seq conf :points))))]))


(defn shortest-distance-*
  "Denotes the shortest distance from a data point to a 
	 center. Which distance to use is decided by the k means 
	 configuration."
  [configuration]
  (let [distance-fn (:distance-fn configuration)]
    (fn [point centroids]
      (apply min (map #(distance-fn point %) centroids)))))

(defn shortest-distance-squared-*
  "Denotes the shortest distance from a data point to a 
	 center squared. Useful for computing a D^2 sampling 
	 distribution."
  [configuration centroids]
  (let [shortest-distance (shortest-distance-* configuration)]
    (fn [point]
      (Math/pow
       (shortest-distance point centroids)
       2))))


;; Helper methods to make setting up chain lengths less of a mental 
;; burden.  When chain lengths aren't provided this code will figure 
;; out a reasonable chain length and set it.

(s/fdef chain-length-warnings :args (s/cat :conf t-config :results t-config))
(defn chain-length-warnings
  "Analyzes the chain length and emits warnings if necessary."
  [config]
  (when (> (:m config) (:size-estimate config))
		;; the monte carlo sampling is intended to approximate the sampling distribution 
		;; computed relative to the entire dataset which is constructed during k means++ 
		;; computation. A larger sample size results in a better approximation, eventually 
		;; converging to the true sampling distribution - at which point the monte carlo simulation 
		;; is just overheard. We aren't eliminating the sampling distribution error, but 
		;; doing wasteful computations.
    (log/warn ":m, the chain length for mc sampling, is greater than the dataset size. You ought to be using k-means++ directly."))
  config)


(s/fdef should-update-chain-length? :args (s/cat :conf t-config :results boolean?))
(defn should-update-chain-length? [conf] (nil? (:m conf)))

(s/fdef update-chain-length :args (s/cat :conf t-config :results t-config))
(defn update-chain-length
  [conf]
	;; We choose to use a default chain length of k*log2(n)log(k)
	;; because this was the chain length used in Bachem's 2016 analysis 
	;; and so it has theoretical guarantees under some conditions.
  (let [n (:size-estimate conf)
        k (:k conf)
        proposed-chain-length (int (* k (/ (Math/log n) (Math/log 2)) (Math/log k)))
        m (min proposed-chain-length (dec n))]
    (assoc conf :m m)))


(s/fdef add-default-chain-length :args (s/cat :conf t-config :results t-config))
(defn add-default-chain-length
  "For monte carlo methods we need a chain length to use when 
	 doing sampling. Although callers can pass in a chain length 
	 there are some dangers when doing so - for example if the 
	 chain length is low it won't necessarily approximate k means 
	 plus plus. Meanwhile if the chain length is too low then 
	 there will be no point in doing sampling at all - we could 
	 just use k means plus plus rather than approximating it.

	 This function checks to see if a chain length is set and if 
	 one is then it does nothing, but it nothing is set it uses 
	 the formulas provided in the k means plus plus approximation 
	 papers to determine a reasonable chain length."
  [conf]
  (->
   (if (should-update-chain-length? conf)
     (update-chain-length conf)
     conf)
   chain-length-warnings))