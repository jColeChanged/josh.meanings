(ns josh.meanings.classify
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
            [clojure.test.check.generators :as gen]
            [tech.v3.dataset :as ds]))

(set! *warn-on-reflection* true)


(defn sum 
  "Returns the sum of the numbers in the sequence."
  [coll]
  (reduce + 0 coll))

(s/fdef sum
  :args (s/cat :coll (s/coll-of number?))
  :ret number?)
  
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

