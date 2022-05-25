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

  (:require
   [clojure.tools.logging :as log]
   [tech.v3.dataset.reductions :as ds-reduce]
   [tech.v3.dataset :as ds]
   [bigml.sampling.reservoir :as res-sample]
   [clojure.string])
  (:use
   [josh.meanings.persistence :as persist]
   [clojure.java.io :as io]
   [tech.v3.dataset.math :as ds-math]
   [fastmath.vector :as math])
  (:gen-class))

(set! *warn-on-reflection* true)

;; k is the number of clusters. 
;; 
;; State is tracked indirectly via files so that we can run 
;; on datasets that are too large to fit in memory. Points, centroids, 
;; assignments are all this type of file references. 
(defrecord KMeansState
           [k
            points
            centroids
            assignments
            format
            init
            distance-fn])


;; k-means-||-initialization includes k-means-++-initialization 
;; as well as k-means itself as a special case. This introduces 
;; a circular dependency. So we don't lay out initialization schemes 
;; via a map. Instead we rely on defmulti provide dispatch. Neverthless 
;; it is valuable to be able to know the supported dispatch keys as a 
;; caller. So we track that here.
(def initialization-schemes
  "Supported initialization methods for generating initial centroids."
  #{:classical :random :k-means-++ :k-means-parallel})

(defmulti initialize-centroids
  "Initializes centroids according to the initialization 
   method specified in the configuration."
  :init)



(def *default-format* :parquet)
(def *default-init*   :k-means-++)

(defn initialize-k-means-state
  "Sets initial configuration options for the k means calculation."
  [points-file k options]
  (log/debug "Validating k-means options")
  (let [format (or (:format options) *default-format*)
        init (or (:init options) *default-init*)
        distance-fn math/dist-emd]
    (when (not (contains? persist/formats format))
      (throw (Exception. (str "Invalid format provided. Format must be one of " (keys persist/formats)))))
    (when (not (contains? initialization-schemes init))
      (throw (Exception. (str "Invalid initialization scheme provided. Scheme must be of one of "
                              (keys initialization-schemes)))))
    (log/debug "Validated k mean options")
    (log/info "Generating k mean configuration")
    (persist/csv-seq-filename->format-seq (KMeansState.
                                           k
                                           points-file
                                           (persist/change-extension (persist/centroids-filename points-file) :csv)
                                           (persist/change-extension (persist/assignments-filename points-file) format)
                                           format
                                           init
                                           distance-fn) :points)))







;; [clojure.core.reducers :as reducers]

;; (defn find-domain
;;   "Calculates the min and max for each column in the points dataset."
;;   [^KMeansState configuration]
;;   (log/info "Finding the domain")
;;   (reducers/fold
;;    (fn
;;      ([] [(repeat Integer/MAX_VALUE) (repeat Integer/MIN_VALUE)])
;;      ([x y]
;;       [(math/emn (first x) (first y))
;;        (math/emx (second x) (second y))]))
;;    (eduction
;;     (comp
;;      (map #(ds/brief % {:stat-names [:min :max]}))
;;      (map (juxt
;;            (partial map :min)
;;            (partial map :max))))
;;     (read-dataset-seq configuration :points))))


;; (defn random-between
;;   "Generate a random number between [[min max]]."
;;   [[min max]]
;;   (+ (* (rand) (- max min)) min))

;; (defn random-centroid
;;   "Generate a random point within the domain."
;;   [domain]
;;   (map random-between
;;        (map vector (first domain) (second domain))))

;; (defmethod initialize-centroids
;;   :random
;;   [k-means-state]
;;   (log/info "Performing random (naive) k means initialization")
;;   (let [k (:k k-means-state)
;;         domain (find-domain k-means-state)
;;         rows->maps (partial ds-seq->rows->maps
;;                             (read-dataset-seq k-means-state :points))]
;;     (log/info "Generating initial centroids")
;;     (ds/->dataset (rows->maps (repeatedly k #(random-centroid domain))))))


(defn ds-seq->rows->maps
  [ds-seq rows]
  (let [column-names (dataset-seq->column-names ds-seq)]
    (map #(zipmap column-names %) rows)))








(defn find-closest-centroid
  [^KMeansState configuration]
  (let [distance-fn (:distance-fn configuration)]
    (fn [centroids point]
      (let [distances (map (partial distance-fn point) centroids)]
        (first (apply min-key second (map-indexed vector distances)))))))

(defn assign-clusters
  [k-means-state]
  (log/info "Generating assignments")
  (let [datasets (read-dataset-seq k-means-state :points)
        columns (dataset-seq->column-names datasets)
        to-vec (fn [row] (map #(get row %) columns))
        assign (comp
                (partial hash-map :assignment)
                (partial
                 (find-closest-centroid k-means-state)
                 (persist/read-centroids-from-file k-means-state))
                to-vec)
        map-assignment (fn [dataset] (ds/row-map dataset assign))]
    (write-dataset-seq k-means-state :assignments (map map-assignment datasets))))


(defn calculate-objective
  [k-means-state]
  (log/info "Calculating objective")
  (let [centroids (persist/read-centroids-from-file k-means-state)
        assign->centroid (partial nth centroids)
        assigns->centroids (comp (map ds/rows)
                                 (map (partial map #(% "assignment")))
                                 (map (partial map assign->centroid)))]
    (reduce + 0
            (map (partial reduce + 0)
                 (map (partial map (:distance-fn k-means-state))
                      (eduction
                       assigns->centroids
                       (read-dataset-seq k-means-state :assignments))
                      (eduction (map ds/rowvecs) (read-dataset-seq k-means-state :points)))))))





(defn update-centroids
  [k-means-state]
  (log/info "Recalculating centroids based on assignments")
  (let [column-names (persist/dataset-seq->column-names
                      (persist/read-dataset-seq k-means-state :assignments))]
    (ds/drop-columns
     (ds-reduce/group-by-column-agg
      "assignment"
      (zipmap
       column-names
       (map ds-reduce/mean column-names))
      (persist/read-dataset-seq k-means-state :assignments))
     ["assignment"])))


(defn recalculate-means
  [k-means-state]
  (let [centroids (update-centroids k-means-state)]
    (persist/write-dataset-seq k-means-state :centroids centroids)
;;    (ds/write! centroids (:centroids k-means-state))
    centroids))

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





;; We never want to rely on things fitting in memory, but in practice 
;; forcing a narrow calling convention on anyone wishing to do a k-means 
;; calculation makes the library harder to use.
;; 
;; To help get around that we are willing to allow anything that we can 
;; transform into a dataset. We handle the transformation step via 
;; multimethods which dispatch based on the datasets type.
(defmulti k-means
  (fn [dataset _ & _]
    (class dataset)))

;; In the ideal we don't have any work to do. We've already got a 
;; reference to the file we were hoping for.
(defmethod k-means
  java.lang.String
  [points-filepath k & options]
  (let [k-means-state (initialize-k-means-state points-filepath k options)
        initial-centroids (initialize-centroids k-means-state)]
    (write-dataset-seq k-means-state :centroids initial-centroids)
    ;; (ds/write! initial-centroids (:centroids k-means-state))
    (log/info "Starting optimization process for" k-means-state)
    (loop [centroids initial-centroids
           centroids-history []]
      (assign-clusters k-means-state)
      (let [new-centroids (recalculate-means k-means-state)]
        (if (stabilized? new-centroids centroids)
          new-centroids
          (recur new-centroids (conj centroids-history centroids)))))))

;; If we don't get a reference to our file, we'll have to create it.
;; We don't want to support things that are too large to fit in memory
;; even still so we're accepting lazy seqs and not everything. 
;; 
;; Really we're fine with accepting anything that can be understood by 
;; ds/->dataset. 
(defmethod k-means
  clojure.lang.LazySeq
  [lazy-seq k & options]
  ;; We try to get a unique filename and we try to avoid writing to a 
  ;; format and we try to avoid the extra work of converting between 
  ;; formats if we can help it.
  (let [format (or (:format options) *default-format*)
        suffix (:suffix (format formats))
        ;; I didn't want to use a full path like you would get with a temp 
        ;; file here, because doing so would break the namespacing of `centroids.`
        ;; and `assignments.` 
        ;; 
        ;; The namespacing mechanism really ought to be better than this, but 
        ;; I want to get things working before I try to make things clean. 
        filename (str (java.util.UUID/randomUUID) suffix)]
    (ds/write!
     (ds/->dataset lazy-seq)
     filename)
    (println "Options are" options)
    (apply k-means filename k options)))







;; Initializations 

(defn shortest-distance-*
  "Denotes the shortest distance from a data point to a 
   center. Which distance to use is decided by the k means 
   configuration."
  [^KMeansState configuration]
  (let [distance-fn (:distance-fn configuration)]
    (fn [point centroids]
      (apply min (map #(distance-fn point %) centroids)))))

(defn k-means-++-weight
  [configuration centroids]
  (let [shortest-distance (shortest-distance-* configuration)]
    (fn [point]
      (Math/pow
       (shortest-distance point centroids)
       2))))


(defn uniform-sample
  [ds-seq n]
  (apply res-sample/merge
         (map #(res-sample/sample (ds/rowvecs %) n) ds-seq)))

(defn weighted-sample
  [ds-seq weight-fn n]
  (apply res-sample/merge
         (map
          #(res-sample/sample
            (ds/rowvecs %) n
            :weigh weight-fn)
          ds-seq)))



(defmethod initialize-centroids
  :classical
  [k-means-state]
  (log/info "Performing classical (naive) k means initialization")
  (let [k (:k k-means-state)
        rows->maps (partial ds-seq->rows->maps
                            (read-dataset-seq k-means-state :points))]
    (ds/->dataset (rows->maps (uniform-sample (read-dataset-seq k-means-state :points) k)))))

(defmethod initialize-centroids
  :k-means-++
  [k-means-state]
  (log/info "Performing k means++ initialization")
  (let [ds-seq (read-dataset-seq k-means-state :points)
        k (:k k-means-state)
        rows->maps (partial ds-seq->rows->maps ds-seq)]
    (loop [centers (uniform-sample ds-seq 1)]
      (if (= k (count centers))
        (ds/->dataset (rows->maps centers))
        (recur (concat centers
                       (weighted-sample ds-seq
                                        (k-means-++-weight k-means-state centers)
                                        1)))))))


(defmethod initialize-centroids
  :k-means-parallel
  [k-means-state]
  (log/info "Performing k means parallel initialization")
  (let [ds-seq (read-dataset-seq k-means-state :points)
        k (:k k-means-state)
        oversample-factor (* 2 k)
        iterations 5
        rows->maps (partial ds-seq->rows->maps ds-seq)]
    (loop [i 0
           centers (uniform-sample ds-seq 1)]
      (if (= i iterations)
        (do
          (log/info "Finished oversampling. Reducing to k centroids")
          (k-means (rows->maps centers) k :init :k-means-++ :distance-fn (:distance-fn k-means-state)))
        (recur (inc i) (concat centers
                               (weighted-sample ds-seq
                                                (k-means-++-weight k-means-state centers)
                                                oversample-factor)))))))
