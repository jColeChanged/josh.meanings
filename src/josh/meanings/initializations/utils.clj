(ns josh.meanings.initializations.utils
  (:require [bigml.sampling.reservoir :as res-sample]
            [tech.v3.dataset :as ds]
            [clojure.tools.logging :as log]
            [josh.meanings.persistence :as persist]
            [clojure.spec.alpha :as s]))

(def t-config :josh.meanings.specs/configuration)
(def t-points :josh.meanings.specs/points)


(s/fdef centroids->dataset :args (s/cat :conf t-config :results t-points))
(defn centroids->dataset
  [conf results]
  {:pre [(= (count results) (:k conf))]
   :post [(= (count results) (ds/row-count %))]}
  (ds/->dataset
   (persist/ds-seq->rows->maps
    (persist/read-dataset-seq conf :points)
    results)))


(defn uniform-sample
  [ds-seq n & options]
  (log/debug "Getting uniform sample of size" n)
  (let [sample #(apply res-sample/sample (ds/rowvecs %) n options)]
    (apply res-sample/merge (map sample ds-seq))))

(defn weighted-sample
  [ds-seq weight-fn n & options]
  (log/debug "Getting weighted sample of size" n)
  (let [sample #(apply res-sample/sample (ds/rowvecs %) n :weigh weight-fn options)]
    (shuffle (apply res-sample/merge (pmap sample ds-seq)))))

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
  (when (< (:m config) (:size-estimate config))
    ;; the monte carlo sampling is intended to approximate the sampling distrubtion 
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
  {:post [(integer? (:m %))]}
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
   plus plus. Meanwhile if the chian length is too low then 
   there will be no point in doing sampling at all - we could 
   just use k means plus plus rather than approximating it.

   This function checks to see if a chain length is set and if 
   one is then it does nothing, but it nothing is set it uses 
   the formulas provided in the k means plus plus apporximation 
   papers to ddetermine a reasonable chain length."
  [conf]
  (->
   (if (should-update-chain-length? conf)
     (update-chain-length conf)
     conf)
   chain-length-warnings))

