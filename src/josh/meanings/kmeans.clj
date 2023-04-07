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
            [josh.meanings.records.clustering-state :refer [->KMeansState]]
            [josh.meanings.records.cluster-result :refer [map->ClusterResult]]
            [josh.meanings.protocols.savable :refer [Savable]]
            [josh.meanings.protocols.classifier :refer [Classifier]]
            [josh.meanings.initializations
             [niave :as init-naive]
             [mc2 :as init-mc2]
             [afk :as init-afk]
             [plusplus :as init-plusplus]
             [parallel :as init-parallel]]
            [josh.meanings.distances :as jmdistance]
            [josh.meanings.initializations.core :refer [initialize-centroids]]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.reductions :as ds-reduce])
  (:use
   [josh.meanings.persistence :as persist]
   [josh.meanings.initializations.niave]
   [clojure.java.io :as io]
   [tech.v3.dataset.math :as ds-math])
  (:import
   [josh.meanings.records.clustering_state KMeansState]
   [josh.meanings.records.cluster_result ClusterResult]))

(set! *warn-on-reflection* true)


(defn sum
  "Returns the sum of the numbers in the sequence."
  [coll]
  (reduce + 0 coll))

(s/fdef sum
  :args (s/cat :coll (s/coll-of number?))
  :ret number?)



(defn initialize-centroids!
  "Calls initialize-centroids and writes the returned dataset to the centroids file."
  [^KMeansState s]
  (let [centroids (initialize-centroids s)]
    (persist/write-dataset s :centroids centroids)
    centroids))

(def default-run-count 3)


(declare k-means)




(def default-format       :parquet)
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
  (sum (map ds/row-count (persist/read-dataset-seq filepath))))

(defn column-names
  [filepath]
  (remove #{"assignments" "q(x)"} (ds/column-names (first (persist/read-dataset-seq filepath)))))


(defn validate-options
  "Validates the given options map, ensuring that all required options are present and valid."
  [options]
  (let [format (or (:format options) default-format)]
    (is (contains? persist/formats format))))

(defn initialize-k-means-state
  "Sets initial configuration options for the k means calculation."
  [points-file k options]
  {:pre [(map? options) (validate-options options)]}
  (let [{:keys [format init distance-key m]} (merge default-options options)
        distance-fn (jmdistance/get-distance-fn distance-key)
        points-file (persist/convert-file points-file format)
        col-names (get options :columns (column-names points-file))]
    (log/info "Generating k mean configuration")
    (->KMeansState
     k
     points-file
     (persist/centroids-filename points-file)
     (persist/assignments-filename points-file)
     format
     init
     distance-key
     distance-fn
     m
     k-means
     (estimate-size points-file)
     col-names)))


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
    (assoc points "assignments" (assignments (ds->rows centroids) distance-fn (ds->rows points)))))

(s/fdef dataset-assignments
  :args (s/cat :centroids ds/dataset?
               :distance-fn #(or (fn? %) (ifn? %))
               :cols (s/coll-of #(or (string? %) (keyword? %)) :min-count 1)
               :points ds/dataset?)
  :ret ds/dataset?
  :fn (s/and
       ;; Test that the returned dataset contains an assignments column
       #(contains? (ds/column-names (:ret %)) "assignments")
        ;; Test that the number of assignments in the returned dataset is equal to the number of points
       #(= (-> % :args :points ds/row-count) (-> % :ret (ds/select-columns ["assignments"]) ds/row-count))
        ;; Test that the assignments in the returned dataset are gettable in centroids
       #(let [centroids (-> % :args :centroids ds/value-reader)
              assignments (-> % :ret :assignments)]
          (every? (fn [a] (contains? centroids a)) assignments))))


(defn dataset-assignments-seq
  "Updates a sequence of assignment datasets with the new assignments."
  [centroids distance-fn cols points-seq]
  (for [points points-seq] (dataset-assignments centroids distance-fn cols points)))

(s/fdef dataset-assignments-seq
  :args (s/cat :centroids ds/dataset?
               :distance-fn #(or (fn? %) (ifn? %))
               :cols (s/coll-of #(or (string? %) (keyword? %)) :min-count 1)
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


(defn cost
  "Returns the distance of an assigned point from its centroid."
  [centroids distance-fn assignment point]
  (distance-fn (centroids assignment) point))

(s/fdef cost
  :args (s/cat :centroids (s/coll-of (s/coll-of number? :min-count 1) :min-count 1)
               :distance-fn #(or (fn? %) (ifn? %))
               :assignment nat-int?
               :point (s/coll-of number? :min-count 1))
  :ret number?
  :fn (s/and
       ;; Test that the cost is non-negative
       #(not (neg? (:ret %)))))

(defn costs
  "Returns the distances of the points from their centroids."
  [centroids distance-fn assignments points]
  (map (partial cost centroids distance-fn) assignments points))

(s/fdef costs
  :args (s/cat :centroids (s/or
                           :buffer #(and (sequential? %) (pos? (count %)))
                           :vec (s/coll-of (s/coll-of number? :min-count 1) :min-count 1))
               :distance-fn #(or (fn? %) (ifn? %))
               :assignments (s/or
                             :buffer #(and (sequential? %) (pos? (count %)))
                             :vec (s/coll-of number? :min-count 1))
               :points (s/or
                        :buffer #(and (sequential? %) (pos? (count %)))
                        :vec (s/coll-of (s/coll-of number? :min-count 1) :min-count 1)))
  :ret (s/coll-of number? :min-count 1)
  :fn (s/and
       ;; Test that the number of costs returned is equal to the number of points.
       #(= (-> % :args :points count) (-> % :ret count))))

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
      "assignments"
      (zipmap
       column-names
       (map ds-reduce/mean column-names))
      (persist/read-dataset-seq s :assignments))
     ["assignments"])))


(defn recalculate-means
  [^KMeansState s]
  (let [centroids (update-centroids s)]
    (persist/write-dataset s :centroids centroids)
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
  (^ClusterResult [points-filepath k & options]
   {:pre [(is (valid? :josh.meanings.specs/k k))
          (is (valid? string? points-filepath))]}
   (let [^KMeansState k-means-state (initialize-k-means-state points-filepath k (apply hash-map options))
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
            {:centroids (:centroids k-means-state)
             :assignments (:assignments k-means-state)
             :cost (calculate-objective k-means-state)
             :configuration (.configuration k-means-state)})
           (recur new-centroids (conj centroids-history centroids))))))))


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