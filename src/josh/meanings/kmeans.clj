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

  (:require [clojure.spec.alpha :as s :refer [valid?]]
            [clojure.test.check.generators :as gen]
            [clojure.string]
            [clojure.test :refer [is]]
            [clojure.tools.logging :as log]
            [josh.meanings.initializations
             [niave :as init-naive]
             [mc2 :as init-mc2]
             [afk :as init-afk]
             [plusplus :as init-plusplus]
             [parallel :as init-parallel]]
            [josh.meanings.distances :as jmdistance]
            [josh.meanings.initializations.core :refer [initialize-centroids]]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.reductions :as ds-reduce]
            [clojure.test.check.generators :as gen])
            ;;[tablecloth.api :as tc]
            ;;[tech.v3.datatype.functional :as dfn])
  (:use
   [josh.meanings.persistence :as persist]
   [josh.meanings.initializations.niave]
   [clojure.java.io :as io]
   [tech.v3.dataset.math :as ds-math]))

(set! *warn-on-reflection* true)

(defprotocol PClusterModel
  (save-model [this filename])
  (load-assignments [this])
  (classify [this x]))

(defrecord ClusterResult
  [centroids ;; A vector of points
   cost      ;; The total distance between centroids and assignments
   configuration  ;; a map of details about the configuration used to generate the cluster result
  ]
  PClusterModel 
  (save-model [this filename] (spit filename (pr-str this)))
  (load-assignments [this] (ds/->dataset (:assignments (:configuration this)) {:key-fn keyword}))
  (classify [this point] (apply min-key (map (partial (:distance-fn point)) (:centroids this)))))

(defn load-model
  [filename]
  (read-string (slurp filename)))

(defprotocol PPersistence
  
  (column-names [this])
  (load-centroids [this])
  (load-points [this])
  (load-assignments [this]))





(defrecord KMeansState
           [k  ;; Number of clusters

            ;; State is tracked indirectly via files so that we can run 
            ;; on datasets that are too large to fit in memory. Points, centroids, 
            ;; assignments are all this type of file references. 
            points
            centroids
            assignments

            format ;; The format that will be used to store the points, centroids and assignments.
            init   ;; The initialization method that will be used to generate the initial centroids.
            distance-key  ;; The key that will be used to determine the distance function to use.
            distance-fn   ;; The distance function itself, derived from the distance-key.
            m             ;; The chain length to use when doing monte carlo sampling if applicable.
            k-means       ;; A reference to the k-means function; sometimes k means classification requires recursion.
            size-estimate ;; An estimate of the size of the dataset.  Sometimes useful in initialization methods and sanity checks. 
            ]

  PPersistence
  (load-centroids
    [this]
    (-> (:centroids this)
        (ds/->dataset {:header-row? true :key-fn keyword})))

  (load-points
    [this]
    (persist/read-dataset-seq this :points))

  (load-assignments
    [this]
    (persist/read-dataset-seq this :points))

  (column-names
    [this]
    (remove #{:assignments (keyword "q(x)")}
            (-> (:centroids this)
                (ds/->dataset {:header-row? true :key-fn keyword})
                (ds/column-names)))))


(defn estimate-size
  "Update the number of records in the dataset."
  [config]
  (assoc config :size-estimate (reduce + (map ds/row-count  (persist/read-dataset-seq config :points)))))

(defn initialize-centroids!
  [k-means-state]
  (let [centroids (initialize-centroids k-means-state)]
    (persist/write-dataset-seq k-means-state :centroids centroids)
    centroids))



(declare k-means)

(def default-format :parquet)
(def default-init   :afk-mc)
(def default-distance-fn :emd)
(def default-run-count 3)

(defn initialize-k-means-state
  "Sets initial configuration options for the k means calculation."
  [points-file k options]
  (log/debug "Validating k-means options")
  (let [format (or (:format options) default-format)
        init (or (:init options) default-init)
        distance-key (or (:distance-fn options) default-distance-fn)
        distance-fn (jmdistance/get-distance-fn distance-key)
        m (or (:m options) 200)]
    (when (not (contains? persist/formats format))
      (throw (Exception. (str "Invalid format provided. Format must be one of " (keys persist/formats)))))
    (log/debug "Validated k mean options")
    (log/info "Generating k mean configuration")
    (->
     (persist/csv-seq-filename->format-seq (KMeansState.
                                            k
                                            points-file
                                            (persist/change-extension (persist/centroids-filename points-file) :csv)
                                            (persist/change-extension (persist/assignments-filename points-file) format)
                                            format
                                            init
                                            distance-key
                                            distance-fn
                                            m
                                            k-means
                                            nil) :points)
     estimate-size)))




(defn min-index
  "Returns the index of the minimum value in a collection."
  [coll]
  (first (apply min-key second (map-indexed vector coll))))

(s/def ::non-empty-seq-of-numbers
  (s/with-gen
    (s/coll-of number? :min-count 1)
    #(gen/not-empty (gen/vector gen/nat))))

(s/fdef min-index
  :args (s/cat :coll ::non-empty-seq-of-numbers)
  :ret nat-int?
  :fn (s/and
       ;; Test that the return value is a valid index
       #(< (:ret %) (-> % :args :coll count))
       ;; Test that the value at the returned index is equal to the minimum value
       #(let [coll (-> % :args :coll)]
          (= (nth coll (:ret %)) (apply min coll)))))


(defn max-index
  "Returns the index of the minimum value in a collection."
  [coll]
  (first (apply max-key second (map-indexed vector coll))))

(s/fdef max-index
  :args (s/cat :coll ::non-empty-seq-of-numbers)
  :ret nat-int?
  :fn (s/and
       ;; Test that the return value is a valid index
       #(< (:ret %) (-> % :args :coll count))
       ;; Test that the value at the returned index is equal to the maximum value
       #(let [coll (-> % :args :coll)]
          (= (nth coll (:ret %)) (apply max coll)))))


(defn distances
  "Returns a vector of distance of the centroids from the point."
  [centroids distance-fn point]
  (map (partial distance-fn point) centroids))

(s/fdef distances
  :args (s/cat :centroids (s/or
                           :buffer #(and (sequential? %) (pos? (count %)))
                           :vec (s/coll-of (s/coll-of number? :min-count 1) :min-count 1))
               :distance-fn #(or (fn? %) (ifn? %))
               :point (s/coll-of number? :min-count 1))
  :ret (s/coll-of number? :min-count 1)
  :fn (s/and
       ;; Test that distances are non-negative 
       #(every? (complement neg?) (:ret %))
       ;; Test that the number of distances returned is equal to the number of centroids
       #(= (-> % :args :centroids count) (-> % :ret count))))


(defn classify
  "Returns the index of the centroid that is closest to the point."
  [centroids distance-fn point]
  (min-index (distances centroids distance-fn point)))


(s/fdef classify 
  :args (s/cat :centroids (s/coll-of (s/coll-of number? :min-count 1) :min-count 1)
               :distance-fn #(or (fn? %) (ifn? %))
               :point (s/coll-of number? :min-count 1))
  :ret nat-int?
  :fn (s/and
       ;; Test that the return value is a valid index
        #(< (:ret %) (-> % :args :centroids count))))


(defn assignments
  "Returns the assignments of the points to the centroids."
  [centroids distance-fn points]
  (map (partial classify centroids distance-fn) points))


(s/fdef assignments
  :args (s/cat :centroids (s/or
                           :buffer #(and (sequential? %) (pos? (count %)))
                           :vec (s/coll-of (s/coll-of number? :min-count 1) :min-count 1))
               :distance-fn #(or (fn? %) (ifn? %))
               :points (s/or
                        :buffer #(and (sequential? %) (pos? (count %)))
                        :vec (s/coll-of (s/coll-of number? :min-count 1) :min-count 1)))
  :ret (s/coll-of number? :min-count 1)
  :fn (s/and
       ;; Test that the number of assignments returned is equal to the number of points.
       #(= (-> % :args :points count) (-> % :ret count))))

(defn dataset-assignments
  "Updates the assignments dataset with the new assignments."
  [centroids distance-fn cols points]
  (let [ds->rows #(-> % (ds/select-columns cols) ds/rowvecs)]
    (assoc points :assignments (assignments (ds->rows centroids) distance-fn (ds->rows points)))))

(s/fdef dataset-assignments 
  :args (s/cat :centroids ds/dataset?
               :distance-fn #(or (fn? %) (ifn? %)) 
               :cols (s/coll-of keyword? :min-count 1)
               :points ds/dataset?)
  :ret ds/dataset?
  :fn (s/and
       ;; Test that the returned dataset contains an assignments column
        #(contains? (ds/column-names (:ret %)) :assignments)
        ;; Test that the number of assignments in the returned dataset is equal to the number of points
        #(= (-> % :args :points ds/row-count) (-> % :ret (ds/select-columns [:assignments]) ds/row-count))
        ;; Test that the assignments in the returned dataset are gettable in centroids
        #(let [centroids (-> % :args :centroids ds/value-reader)]
               assignments (-> % :ret :assignments)
           (every? (fn [a] (contains? centroids a)) assignments)))) 


(defn dataset-assignments-seq
  "Updates a sequence of assignment datasets with the new assignments."
  [centroids distance-fn cols points-seq] 
  (for [points points-seq] (dataset-assignments centroids distance-fn cols points)))

(s/fdef dataset-assignments-seq
  :args (s/cat :centroids ds/dataset?
               :distance-fn #(or (fn? %) (ifn? %)) 
               :cols (s/coll-of keyword? :min-count 1)
               :points-seq (s/coll-of ds/dataset? :min-count 1))
  :ret (s/coll-of ds/dataset? :min-count 1)
  :fn (s/and
       ;; Test that the returned sequence of datasets contains an assignments column
       #(every? (fn [x] contains? (ds/column-names x) :assignments) (:ret %))
       ;; Test that the number of assignments in the returned sequence of datasets is equal to the number of points
       #(let [points (-> % :args :s first)
              assignments (-> % :ret first :assignments)]
          (= (ds/row-count points) (count assignments)))))


(defn regenerate-assignments!
  "Writes the new assignments to the assignments file."
  [^KMeansState s]
  (persist/write-dataset-seq
   s
   :assignments
   (dataset-assignments-seq (.load-centroids s) (:distance-fn s) (.column-names s) (.load-points s))))


(defn sum 
  "Returns the sum of the numbers in the sequence."
  [numbers]
  (reduce + 0 numbers))

(defn cost
  "Returns the distance of an assigned point from its centroid."
  [centroids distance-fn assignment point] 
  (distance-fn (centroids assignment) point))

(defn costs
  "Returns the distances of the points from their centroids."
  [centroids distance-fn assignments points]
  (map (partial cost centroids distance-fn) assignments points))

(defn dataset-costs
  "Returns the distances of the points from their centroids."
  [centroids distance-fn cols points]
  (let [ds->rows #(-> % (ds/select-columns cols) ds/rowvecs)]
    (costs (ds->rows centroids) distance-fn (-> points :assignments) (ds->rows points))))

(defn dataset-costs-seq
  "Returns the distances of the points from their centroids."
  [centroids distance-fn cols points-seq]
  (for [points points-seq]
    (dataset-costs centroids distance-fn cols points)))


(defn calculate-objective
  [^KMeansState s]
  (log/info "Calculating objective")
  (sum (map sum (dataset-costs-seq (.load-centroids s) (:distance-fn s) (.column-names s) (.load-assignments s)))))


(defn update-centroids
  [^KMeansState s]
  (log/info "Recalculating centroids based on assignments")
  (let [column-names (persist/dataset-seq->column-names (persist/read-dataset-seq s :assignments))]
    (ds/drop-columns
     (ds-reduce/group-by-column-agg
      :assignments
      (zipmap
       column-names
       (map ds-reduce/mean column-names))
      (persist/read-dataset-seq s :assignments))
     [:assignments])))


(defn recalculate-means
  [^KMeansState s]
  (let [centroids (update-centroids s)]
    (persist/write-dataset-seq s :centroids centroids)
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



(defn k-means-via-file
  [points-filepath k & options]
  {:pre [(is (valid? :josh.meanings.specs/k k))
         (is (valid? string? points-filepath))]
   :post [(= (ds/row-count (:centroids %)) k)
          (= (count (set (ds/rowvecs (:centroids %)))) k)]}
  (let [k-means-state (initialize-k-means-state points-filepath k (apply hash-map options))
        initial-centroids (initialize-centroids! k-means-state)]
    (log/info "Got initial centroids" initial-centroids)
    (log/info "Initial centroids has" (ds/row-count initial-centroids) "rows")
    (loop [centroids initial-centroids
           centroids-history []]
      (log/info "Loop is now at" (ds/row-count centroids) "rows")

      (regenerate-assignments! k-means-state)
      (log/info "Assignments after assign-clusters is " (persist/read-dataset-seq k-means-state :assignments))
      (let [new-centroids (recalculate-means k-means-state)]
        (log/info "Got new centroids" new-centroids)
        (log/info "New centroids has" (ds/row-count new-centroids) "rows")
        (if (stabilized? new-centroids centroids)
          (map->ClusterResult
           {:centroids new-centroids
            :cost (calculate-objective k-means-state)
            :configuration k-means-state})
          (recur new-centroids (conj centroids-history centroids)))))))


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


;; When making changes its useful to be able to get a sense of how the change
;; will impact the systems performance. This code block is largely worthless 
;; at run time, but it is here because during development we want to be able to 
;; benchmark differnet changes against each other.
(comment
  (require '[criterium.core :as criterium])
  (criterium/quick-bench ...))