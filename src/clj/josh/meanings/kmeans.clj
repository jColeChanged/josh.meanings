(ns josh.meanings.kmeans
  "K-Means clustering generates a specific number of disjoint, 
	 non-hierarchical clusters. It is well suited to generating globular
	 clusters. The K-Means method is numerical, unsupervised, 
	 non-deterministic and iterative. Every member of a cluster is closer 
	 to its cluster center than the center of any other cluster.

	 The choice of initial partition can greatly affect the final clusters 
	 that result, in terms of inter-cluster and intracluster distances and 
	 cohesion. As a result k means is best run multiple times in order to 
	 avoid the trap of a local minimum."
  (:refer-clojure
   :exclude
   [get nth assoc get-in merge assoc-in update update-in select-keys destructure let fn loop defn defn-])
  (:require [clojure.spec.alpha :as s]
            [clojure.string]
            [ham-fisted.lazy-noncaching :as hfln]
            [josh.meanings.distances :as distances]
            [josh.meanings.initializations
             [mc2 :as init-mc2]
             [afk :as init-afk]
             [plusplus :as init-plusplus]
             [parallel :as init-parallel]]
            [josh.meanings.initializations.core :refer [initialize-centroids]]
            [josh.meanings.persistence :as persist]
            [josh.meanings.protocols.classifier :refer [assignments Classifier]]
            [josh.meanings.records.cluster-result :refer [map->ClusterResult]]
            [josh.meanings.records.clustering-state :refer [->KMeansState]]
            [progrock.core :as pr]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.reductions :as dsr]
            [uncomplicate.neanderthal.core :as ne]
            [clj-fast.clojure.core :refer [get nth assoc get-in merge assoc-in update-in select-keys destructure let fn loop defn defn-]])
  (:import [josh.meanings.records.cluster_result ClusterResult]
           [josh.meanings.records.clustering_state KMeansState]))





(declare k-means)

(def default-format       :arrow)
(def default-init         :afk-mc)
(def default-distance-key :emd)
(def default-chain-length  200)


(def default-options
  {:format       default-format
   :init         default-init
   :distance-key default-distance-key
   :chain-length default-chain-length})


(defn estimate-size
  "Estimates the number of rows in the datset at filepath."
  [filepath]
  (let [stats (dsr/aggregate {"n" (dsr/row-count)} (persist/read-dataset-seq filepath))]
    (first (get stats "n"))))


(defn column-names
  [filepath]
  (vec (remove #{"assignments" "q(x)"} (ds/column-names (first (persist/read-dataset-seq filepath))))))


(defn initialize-k-means-state
  "Sets initial configuration options for the k means calculation."
  [points-file k options]
  (let [{:keys [format init distance-key m]} (merge default-options options)
        points-file (persist/convert-file points-file format)
        col-names (get options :columns (column-names points-file))]
    (->KMeansState
     k
     points-file
     format
     init
     distance-key
     m
     k-means
     (estimate-size points-file)
     col-names
     true)))


(defn assignments-api
  "Updates a sequence of assignment datasets with the new assignments."
  ([^KMeansState conf points-seq]
   (let [assign (fn [ds] (assoc ds "assignments" (distances/minimum-index conf ds)))]
     (hfln/map assign points-seq))))



(extend-type KMeansState
  Classifier
  (assignments [this dataset-seq]
    (assignments-api this dataset-seq)))


(extend-type ClusterResult
  Classifier
  (assignments [this dataset-seq]
    (let [config (-> this
                     :configuration
                     (assoc :centroids (:centroids this)))]
      (assignments config dataset-seq))))


(s/fdef calculate-objective :args (s/cat :s :josh.meanings.specs/configuration) :ret number?)
(defn calculate-objective
  [^KMeansState conf]
  (let [centroid-ds (.load-centroids conf)
        calculate-cost (fn [ds] (ne/sum (distances/minimum-distance conf ds centroid-ds)))]
    (reduce + 0 (hfln/map calculate-cost (.load-points conf)))))


(defn stabilized?
  "K-means is said to be stabilized when performing an
	 iterative refinement (often called a lloyd iteration), 
	 does not result in any shifting of points between 
	 clusters. A stabilized k-means calculation can be 
	 stopped, because further refinement won't produce 
	 any changes."
  [centroids-1 centroids-2]
  ;; The bits of equality can be thought of as a selection of the clusters which have moved positions.  
  ;; When a centroid moves positions, that means its nearest neighbors need to be recalculated.  
  (= centroids-1 centroids-2))


(s/fdef lloyd :args (s/cat :conf :josh.meanings.specs/configuration
                           :initial-centroids :josh.meanings.specs/dataset))
(defn lloyd ^ClusterResult [^KMeansState conf initial-centroids]
  (let [column-names (:col-names conf)
        progress (atom 0)
        progress-bar (pr/progress-bar (get conf :iterations 100))]
    (println "Performing lloyd iteration...")
    (let [final-centroids
          (loop [centroids initial-centroids]
            (if (< @progress 100)
              (do
                (pr/print (pr/tick progress-bar @progress))
                (swap! progress inc)
                (recur
                 (distances/with-centroids centroids
                   (dsr/group-by-column-agg
                    "assignments"
                    (zipmap column-names (map dsr/mean column-names))
                    (assignments conf (persist/read-dataset-seq conf :points))))))
              centroids))]
      (pr/print (pr/done (pr/tick progress-bar @progress)))
      (map->ClusterResult
       {:centroids final-centroids
        :cost 0
        :configuration conf}))))


(defn k-means-via-file
  [points-filepath k & options]
  (let [^KMeansState conf (initialize-k-means-state points-filepath k (apply hash-map options))
        centroids (initialize-centroids conf)]
    (distances/with-gpu-context conf (lloyd conf centroids))))



;; We never want to rely on things fitting in memory, but in practice 
;; forcing a narrow calling convention on anyone wishing to do a k-means 
;; calculation makes the library harder to use.
;; 
;; To help get around that we are willing to allow anything that we can 
;; transform into a dataset. We handle the transformation step via 
;; multimethods which dispatch based on the datasets type.
(defmulti k-means
  "Runs k means clustering on a dataset.
	 
	 (k-means ds 5 options)

	 Options:

		 - :format 
		 - :init
			 - :chain-length
		 - :distance-key 
		 - columns
	 "
  (fn [dataset _ & _] (class dataset)))


;; In the ideal we don't have any work to do. We've already got a 
;; reference to the file we were hoping for.
(defmethod k-means
  java.lang.String
  [points-filepath k & options]
  (apply k-means-via-file points-filepath k options))


;; If we don't get a reference to our file, we'll have to create it.
;; We don't want to support things that are too large to fit in memory
;; even still so we're accepting lazy seqs and not everything.
(defmethod k-means
  clojure.lang.LazySeq
  [lazy-seq k & options]
  (let [format (or (:format options) default-format)
        suffix (:suffix (format persist/formats))
        filename (str (java.util.UUID/randomUUID) suffix)]
    (ds/write! (ds/->dataset lazy-seq) filename)
    (apply k-means filename k options)))


(defn k-means-seq
  "Returns a lazy sequence of m ClusterResult."
  [dataset k & options]
  (repeatedly #(apply k-means dataset k options)))