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
            [uncomplicate.neanderthal.core :as ne])
  (:import [josh.meanings.records.cluster_result ClusterResult]
           [josh.meanings.records.clustering_state KMeansState]))

(set! *warn-on-reflection* true)

(s/fdef initialize-centroids! :args (s/cat :s :josh.meanings.specs/configuration))
(defn initialize-centroids!
  "Calls initialize-centroids and writes the returned dataset to the centroids file."
  [^KMeansState conf] 
  (println "\nStage 1/2: Cluster Initialization")
  (persist/write-dataset conf :centroids (initialize-centroids conf)))


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
    (assignments (:configuration this) dataset-seq)))


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
	;; If the two latest centroids distributions are equal 
	;; then we don't need to generate assignments to know 
	;; that assignments haven't changed. If they had changed 
	;; then the center of the centroids would have changed.
	;; 
	;; We could keep track of whether assignments were changed 
	;; during the generation of assignments step, but if we did 
	;; the cost of doing so would be roughly O(rc) calculations 
	;; where r is the number of rows and c is the number of columns. 
	;; 
	;; Checking here is better than that, because we only need to 
	;; check O(kc) where k is the number of clusters and c is the 
	;; number of columns.
	;;
	;; It is worth noting that this conception of assignment changes 
	;; could have some practical benefits. Any differences we observe 
	;; tell us that there is still room for another round of iterative 
	;; refinement, but they also give us an idea of where in the space 
	;; the iterative refinement might need to take place.
	;; 
	;; Consider the 1D case where the clusters are at [0, 10, 15, 30, 50]. 
	;; We can know if the item at 15 moves to 14 and the item at 10 moves 
	;; to do iterative refinements on elements that are in [0, 10, 15, 30]
	;; because everything at 50 must be closer to 30 than they are to 15. 
	;; With five clusters we reduced the number of nodes that we need to 
	;; refine by 1 cluster worth of nodes, but the more k you have the more 
	;; potential there is to avoid having to do refinement. 
	;;
	;; This is true in two senses. In one sense you have less nodes to 
	;; do refinement on because there are less nodes that need to be 
	;; considered for refinement. In another sense there are less clusters 
	;; you have to consider as having the potential to be reassigned to, 
	;; because there are only so many clusters can you reassign too.
	;;
	;; Interestingly this means that as k increases, the cost savings of 
	;; this sort of selectivitiy about how we continue to refine also 
	;; increases. If k increases we should do this step because it saves 
	;; us time and if r increases we should do this step because k < r.
	;; 
	;; But the really profound thing for me is considering that these 
	;; equality check isn't actually a boolean. It is a special case of 
	;; the selection criteria - we should stop, because if nothing changed, 
	;; the clever selection criteria suggests that there is nothing left 
	;; for us to do.
	;; 
	;; Right now as I write this, this is just an equality check. But in 
	;; the future if I get around to fully optimizing this, this won't be 
	;; an equality check. Instead it will be returning selection criteria.
  [centroids-1 centroids-2]
  (= centroids-1 centroids-2))


(s/fdef lloyd :args (s/cat :conf :josh.meanings.specs/configuration))
(defn lloyd ^ClusterResult [^KMeansState conf]
  (let [column-names (:col-names conf)
        progress (atom 0)
        progress-bar (pr/progress-bar (get conf :iterations 100))]
    (println "\nStage 2/2: Lloyd iterations")
    (let [final-centroids
          (loop [centroids (.load-centroids conf)]
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
      final-centroids)))

    
(defn k-means-via-file 
  [points-filepath k & options]
  (let [^KMeansState conf (initialize-k-means-state points-filepath k (apply hash-map options))]
    (initialize-centroids! conf)
    (distances/with-gpu-context conf
      (lloyd conf)
      (map->ClusterResult
       {:centroids (:centroids conf)
        :assignments (:assignments conf)
        :cost (calculate-objective conf)
        :configuration (.configuration conf)}))))


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