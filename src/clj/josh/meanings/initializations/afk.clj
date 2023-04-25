(ns josh.meanings.initializations.afk
  "Fast and Provably Good Seedings for k-Means is a paper by Olivier Bachem, 
   Mario Lucic, S. Hamed Hassani, and Andreas Krause which introduces an 
   improvement to the monte carlo markov chain approximation of k-means++ 
   D^2 sampling. It accomplishes this by computing the D^2 sampling 
   distribution with respect to the first cluster. This has the practical 
   benefit of removing some of the assumptions, like choice of distance 
   metric, which were imposed in the former framing. As such the name of 
   this algorithm is assumption free k-mc^2. A savvy reader may note that 
   by computing the D^2 sampling distribution as part of the steps this 
   algorithm loses some of the theoretical advantages of the pure markov 
   chain formulation. The paper argues that this is acceptable, because 
   in practice computing the first D^2 sampling distribution ends up paying 
   for itself by reducing the chain length necessary to get convergence 
   guarantees."
  (:refer-clojure
   :exclude
   [get nth assoc get-in merge assoc-in update-in select-keys destructure let fn loop defn defn-])
  (:require
   [clojure.spec.alpha :as s]
   [fastmath.core]
   [ham-fisted.lazy-noncaching :as hfln]
   [josh.meanings.distances :as distance]
   [josh.meanings.initializations.core :refer [initialize-centroids]]
   [josh.meanings.initializations.utils
    :as utils
    :refer [add-default-chain-length sample-one weighted-sample]]
   [josh.meanings.persistence :as p]
   [progrock.core :as pr]
   [taoensso.timbre :as log]
   [tech.v3.dataset :as ds]
   [tech.v3.dataset.neanderthal :refer [dataset->dense]]
   [uncomplicate.neanderthal.core :as ne :refer [axpy dim entry! sum]]
   [uncomplicate.neanderthal.native :refer [fv]]
   [uncomplicate.neanderthal.vect-math :as vm]
   [clojure.core :as c]
   [clj-fast.clojure.core :refer [get nth assoc get-in merge assoc-in update-in select-keys destructure let fn loop defn defn-]]
   [josh.meanings.distances :as distances]))


(def qx-column-name "qx")

(s/fdef load-datasets-with-qx 
  :args (s/cat :conf :josh.meanings.specs/configuration)
  :ret :josh.meanings.specs/datasets)
(defn load-datasets-with-qx
  "Load points dataset with q(x)."
  [conf]
  (let [column-names (-> (:col-names conf)
                         (conj qx-column-name))]
    (-> (p/read-dataset-seq "qx.arrow")
        (p/select-columns-seq column-names))))


(s/fdef samples :args (s/cat :conf :josh.meanings.specs/configuration) :ret :josh.meanings.specs/dataset)
(defn samples
  "Get all the samples we'll need for the markov chain."
  ([conf]
   (ds/->dataset 
    (weighted-sample (load-datasets-with-qx conf) qx-column-name (:m conf)))))


(s/fdef qx-denominator-accelerated
  :args (s/cat :device-context map? 
               :conf :josh.meanings.specs/configuration
               :cluster :josh.meanings.specs/dataset)
  :ret number?)
(defn qx-denominator
  "Calculates the denominator of the q(x) distribution."
  [conf cluster]
  (log/info "Calculating q(x) denominator using GPU.")
  (reduce + 0
          (->> (p/read-dataset-seq conf :points)
               (hfln/map (fn [ds] (dataset->dense ds :row :float32)))
               (hfln/map (fn [matrix] (fv (seq (distance/gpu-distance @distance/gpu-context matrix cluster)))))
               (hfln/map (fn [vector] (sum (vm/pow vector 2)))))))


(s/fdef qx-regularizer :args (s/cat :conf :josh.meanings.specs/configuration) :ret number?)
(defn qx-regularizer [conf] (/ 1.0 (* (:size-estimate conf) 2)))


(s/fdef q-of-x
  :args (s/cat :conf :josh.meanings.specs/configuration
               :cluster :josh.meanings.specs/dataset
               :denominator number?)
  :ret :josh.meanings.specs/datasets)
(defn- q-of-x
  "Computes the q(x) distribution for all x in the dataset on the GPU."
  ([conf cluster denominator]
   (log/info "Calculating q(x) using GPUs.")
   (let [regularizer (qx-regularizer conf)
         cluster-matrix (distance/dataset->matrix conf cluster)
         qx (fn [matrix]
              (let [vector (fv (seq matrix))]
                (axpy
                 (/ 1.0 denominator)
                 vector
                 (entry! (fv (seq matrix)) regularizer))))]
     (hfln/map (fn [ds]
                 (assoc ds :qx
                        (->
                         (distance/gpu-distance
                          @distance/gpu-context (distance/dataset->matrix conf ds) cluster-matrix)
                         (qx))))
               (p/read-dataset-seq conf :points)))))



(s/fdef q-of-x! 
  :args (s/cat :conf :josh.meanings.specs/configuration
               :clusters :josh.meanings.specs/dataset))
(defn q-of-x!
  "Computes and saves the q(x) distribution for all x in the dataset."
  ([conf cluster]
   (log/info "Computing q(x) distribution with respect to" cluster)
   (p/write-datasets "qx.arrow"
                     (q-of-x conf cluster (qx-denominator conf (distance/dataset->matrix conf cluster))))))


(s/fdef mcmc-sample
  :args (s/cat :conf     :josh.meanings.specs/configuration
               :points   :josh.meanings.specs/dataset
               :clusters :josh.meanings.specs/dataset)
  :ret :josh.meanings.specs/dataset)
(defn mcmc-sample
  "Perform markov chain monte carlo sampling to approxiate D^2 sampl ing"
  [conf points clusters]
  (let [min-dists (distance/minimum-distance conf points clusters)
        dxqx      (vm/mul min-dists (fv (get points qx-column-name)))
        rands     (ne/view-vctr (utils/generate-random-buffer points))
        cluster-index (reduce
                       (fn [^long acc-index ^long index]
                         (let [acc   (ne/entry dxqx acc-index)
                               dyqy  (ne/entry dxqx index)
                               rand  (ne/entry rands index)]
                           (if (or (zero? acc) (> (/ dyqy acc) rand))
                             index
                             acc-index)))
                       0
                       (range 0 (dim dxqx)))]
    (->  (ds/select-rows points cluster-index)
         (ds/select-columns (:col-names conf)))))


(s/fdef find-next-cluster 
  :args (s/cat :conf :josh.meanings.specs/configuration 
               :clusters :josh.meanings.specs/dataset)
  :ret :josh.meanings.specs/dataset)
(defn find-next-cluster
  "Performs markov chain monte carlo sampling with respect to the 
   distance from existing clusters on a sampling dataset sampled 
   from a q(x) distribution."
  [conf clusters]
  (mcmc-sample conf (samples conf) clusters))


(s/fdef k-means-assumption-free-mc-initialization :args (s/cat :conf :josh.meanings.specs/configuration) :ret :josh.meanings.specs/points)
(defn k-means-assumption-free-mc-initialization
  [conf]
  (distances/with-gpu-context conf
    (log/info "Performing afk-mc initialization with" conf)
    (let [initial-cluster (sample-one conf)
          k (:k conf)
          _ (q-of-x! conf initial-cluster)
          progress-bar (pr/progress-bar k)
          final-clusters (loop [clusters initial-cluster]
                           (let [centroid-count (ds/row-count clusters)]
                             (pr/print (pr/tick progress-bar centroid-count))
                             (if (< centroid-count k)
                               (recur (ds/concat clusters (find-next-cluster conf clusters)))
                               clusters)))]
      (pr/print (pr/done progress-bar))
      final-clusters)))


(defmethod initialize-centroids
  :afk-mc 
  [conf]
  (k-means-assumption-free-mc-initialization (add-default-chain-length conf)))