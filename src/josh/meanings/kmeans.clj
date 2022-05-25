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
   [clojure.core.reducers :as reducers]
   [clojure.tools.logging :as log]
   [tech.v3.libs.arrow :as ds-arrow]
   [tech.v3.dataset.io.csv :as ds-csv]
   [tech.v3.libs.parquet :as ds-parquet]
   [tech.v3.dataset.reductions :as ds-reduce]
   [tech.v3.dataset :as ds]
   [bigml.sampling.reservoir :as res-sample]
   [clojure.string])
  (:use
   [clojure.java.io :as io]
   [tech.v3.dataset.math :as ds-math]
   [fastmath.vector :as math])
  (:import [java.io File])
  (:gen-class))

(set! *warn-on-reflection* true)


;; CSV was extremely slow. Arrow failed to load really large files.
;; Arrows failed to write extremely small files. So I wanted to try 
;; parquet but didn't want to continually have to rewrite parsing 
;; logic as I moved between different file formats. Therefore, I made the 
;; format used a configuration option rather than a hardcoded function 
;; call. If and when parquet fails, I'll have an escape hatch to quickly 
;; try a different file format.
(def formats
  {:parquet
   {:writer ds-parquet/ds-seq->parquet
    :reader ds-parquet/parquet->ds-seq
    :suffix ".parquet"}
   :arrow
   {:writer ds-arrow/dataset-seq->stream!
    :reader ds-arrow/stream->dataset-seq
    :suffix ".arrow"}
   :arrows
   {:writer (fn [path ds-seq]
              (ds-arrow/dataset-seq->stream! path {:format :ipc} ds-seq))
    :reader (fn [path]
              (ds-arrow/stream->dataset-seq path {:format :ipc}))
    :suffix ".arrows"}
   :csv {:writer nil
         :reader ds-csv/csv->dataset-seq
         :suffix ".csv"}})

(defn extension
  "Returns a filenames last file extension."
  [filename]
  (last (clojure.string/split filename #"\.")))

(defn filename->format
  [filename]
  (-> filename
      extension
      keyword))

(defn read-dataset-seq
  [k-means-state key]
  (let [filename (key k-means-state)
        format (filename->format filename)
        reader-fn (-> formats format :reader)]
    (log/info "Loading" filename "with" format)
    (reader-fn filename)))

(defn dataset-seq->column-names
  [ds-seq]
  (ds/column-names (first ds-seq)))

(defn write-dataset-seq
  [k-means-state key dataset]
  (let [filename (key k-means-state)
        format (filename->format filename)
        writer-fn! (-> formats format :writer)]
    (log/info "Writing to" filename "with" format)
    (writer-fn! filename dataset)))

(defn change-extension
  [filename format]
  (let [desired-suffix (:suffix (formats format))]
    (clojure.string/replace filename #"(.*)\.(.*?)$" (str "$1" desired-suffix))))

(defn csv-seq-filename->format-seq
  "Converts a csv file into another file type and 
   returns a k means object with an updated key name.
   
  CSV isn't a well optimized format for doing large computations.                                                        
  Computing the min and max of each column working with optimized 
  csv seqs can take two orders of magnitude longer than the same 
  operations performed against arrow streams."
  [k-means-state key]
  (let [format (:format k-means-state)
        input-filename (key k-means-state)
        new-filename (change-extension input-filename format)
        new-state (assoc k-means-state key new-filename)]
    (log/info "Requested conversion of" input-filename "to" new-filename)
    (when (not= input-filename new-filename)
      (log/info "Converting" input-filename "to" new-filename)
      (write-dataset-seq new-state key (ds-csv/csv->dataset-seq input-filename {:header-row? true}))
      (log/info "Conversion completed"))
    new-state))




;; k is the number of clusters. 
;; 
;; State is tracked indirectly via files so that we can run 
;; on datasets that are too large to fit in memory. Points, centroids, 
;; assignments, and history are all this type of file references. 
(defrecord KMeansState
           [k
            points
            centroids
            assignments
            history
            format
            init
            distance-fn])



(defn generate-filename
  [prefix]
  #(str prefix "." %))

(def centroids-filename (generate-filename "centroids"))

(def assignments-filename (generate-filename "assignments"))

(def history-filename (generate-filename "history"))



(defn file?
  "Returns true if a file exists and false otherwise."
  [filename]
  (.exists (clojure.java.io/file filename)))


;; k-means-||-initialization includes k-means-++-initialization 
;; as well as k-means itself as a special case. This introduces 
;; a circular dependency. So we need to declare the initialization 
;; schemes before we define them if we want to provide a dynamic 
;; and validated choice of init scheme.
(declare k-means-naive-initialization
         k-means-random-initialization
         k-means-++-initialization
         k-means-||-initialization)

(def initialization-schemes
  "Supported initialization methods for generating intial centroids."
  {:classical k-means-naive-initialization
   :random k-means-random-initialization
   :k-means-++ k-means-++-initialization
   :k-means-parallel k-means-||-initialization})

(def *default-format* :parquet)
(def *default-init*   :random)

(defn initialize-k-means-state
  "Sets initial configuration options for the k means calculation."
  [points-file k options]
  (log/debug "Validating k-means options")
  (let [format (or (:format options) *default-format*)
        init (or (:init options) *default-init*)
        distance-fn math/dist-emd]
    (when (not (contains? formats format))
      (throw (Exception. (str "Invalid format provided. Format must be one of " (keys formats)))))
    (when (not (contains? initialization-schemes init))
      (throw (Exception. (str "Invalid initialization scheme provided. Scheme must be of one of "
                              (keys initialization-schemes)))))
    (log/debug "Validated k mean options")
    (log/info "Generating k mean configuration")
    (csv-seq-filename->format-seq (KMeansState.
                                   k
                                   points-file
                                   (change-extension (centroids-filename points-file) :csv)
                                   (change-extension (assignments-filename points-file) format)
                                   (change-extension (history-filename points-file) :csv)
                                   format
                                   init
                                   distance-fn) :points)))


;; Read and realize centroids from a file.
(defn read-centroids-from-file
  [k-means-state]
  (ds/rowvecs
   (ds/->dataset
    (:centroids k-means-state) {:file-type :csv :header-row? true})))



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



(defn find-domain [k-means-state]
  (log/info "Finding the domain")
  (reducers/fold
   (fn
     ([] [(repeat Integer/MAX_VALUE) (repeat Integer/MIN_VALUE)])
     ([x y]
      [(math/emn (first x) (first y))
       (math/emx (second x) (second y))]))
   (eduction
    (comp
     (map #(ds/brief % {:stat-names [:min :max]}))
     (map (juxt
           (partial map :min)
           (partial map :max))))
    (read-dataset-seq k-means-state :points))))

;; Generate a random point within the domain
(defn random-between [[min max]]
  (+ (* (rand) (- max min)) min))

(defn random-centroid [domain]
  (map random-between
       (map vector (first domain) (second domain))))

(defn k-means-random-initialization
  [k-means-state]
  (let [k (:k k-means-state)
        domain (find-domain k-means-state)
        column-names (dataset-seq->column-names
                      (read-dataset-seq k-means-state :points))]
    (log/info "Generating initial centroids")
    (ds/->dataset (vec
                   (map #(zipmap column-names %) (repeatedly k #(random-centroid domain)))))))


(defn k-means-naive-initialization
  [k-means-state]
  (uniform-sample (read-dataset-seq k-means-state :points) (:k k-means-state)))

(defn k-means-++-initialization
  [k-means-state]
  (let [ds-seq (read-dataset-seq k-means-state :points)
        k (:k k-means-state)]
    (loop [centers (uniform-sample ds-seq 1)]
      (if (= k (count centers))
        centers
        (recur (concat centers
                       (weighted-sample ds-seq
                                        (k-means-++-weight k-means-state centers)
                                        1)))))))






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
                 (read-centroids-from-file k-means-state))
                to-vec)
        map-assignment (fn [dataset] (ds/row-map dataset assign))]
    (write-dataset-seq k-means-state :assignments (map map-assignment datasets))))


(defn calculate-objective
  [k-means-state]
  (log/info "Calculating objective")
  (let [centroids (read-centroids-from-file k-means-state)
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
  (let [column-names (dataset-seq->column-names
                      (read-dataset-seq k-means-state :assignments))]
    (ds/drop-columns
     (ds-reduce/group-by-column-agg
      "assignment"
      (zipmap
       column-names
       (map ds-reduce/mean column-names))
      (read-dataset-seq k-means-state :assignments))
     ["assignment"])))







(defn recalculate-means
  [k-means-state]
  (let [centroids (update-centroids k-means-state)]
    (ds/write! centroids (:centroids k-means-state))
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


(defn initialize-centroids
  "Initializes centroids according to the provided centroid
   initialization configuration."
  [^KMeansState configuration]
  (let [init-selection (:init configuration)
        init-method (initialization-schemes init-selection)]
    (when (nil? init-method)
      (throw (Exception. (str "No initialization scheme found for " init-method))))
    (log/info "Initializing centroids with" init-selection "initialization scheme.")
    (let [centroids (init-method configuration)]
      (ds/write! centroids (:centroids configuration))
      centroids)))


;; We never want to rely on things fitting in memory, but in practice 
;; forcing a narrow calling convention on anyone wishing to do a k-means 
;; calculation makes the library harder to use.
;; 
;; To help get around that we are willing to allow anything that we can 
;; transform into a dataset. We handle the transformation step via 
;; multimethods which dispatch based on the datasets type.
(defmulti k-means-six
  (fn [dataset _ & _]
    (class dataset)))

;; In the ideal we don't have any work to do. We've already got a 
;; reference to the file we were hoping for.
(defmethod k-means-six
  java.lang.String
  [points-filepath k & options]
  (let [k-means-state (initialize-k-means-state points-filepath k options)]
    (log/info "Starting optimization process for" k-means-state)
    (loop [centroids (initialize-centroids k-means-state)
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
(defmethod k-means-six
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
    (apply k-means-six filename k options)))






(defn k-means-||-initialization
  [k-means-state]
  (let [ds-seq (read-dataset-seq k-means-state :points)
        k (:k k-means-state)
        oversample-factor (* 2 k)
        iterations 5
        column-names (ds/column-names (first ds-seq))
        rows->maps #(zipmap column-names %)]
    (loop [i 0
           centers (uniform-sample ds-seq 1)]
      (if (= i iterations)
        (k-means-six (map rows->maps centers) k :init :k-means-++ :distance-fn (:distance-fn k-means-state))
        (recur (inc i) (concat centers
                               (weighted-sample ds-seq
                                                (k-means-++-weight k-means-state centers)
                                                oversample-factor)))))))

(comment
  (def state (initialize-k-means-state "test.csv" 5 {}))
  (def parallel-points (k-means-||-initialization state))
  (k-means-||-initialization state))