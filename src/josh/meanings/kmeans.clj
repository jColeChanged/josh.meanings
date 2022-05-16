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
   [clojure.string])
  (:use
   [clojure.java.io :as io]
   [tech.v3.dataset.math :as ds-math]

   [fastmath.vector :as math])
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

(comment
  (def state (initialize-k-means-state "test.csv" 5))
  (clojure.string/replace "test.test.test" #"(.*)\.(.*?)$" "$1.boohoo")
  (csv-seq-filename->format-seq state :points))



;; k is the number of clusters. 
;; 
;; State is tracked indirectly via files so that we can run 
;; on datasets that are too large to fit in memory. Points, centroids, 
;; assignments, and history are all this type of file references. 
;; 
;; Domain is a vector of mins and a vector maxes for each column.
(defrecord KMeansState
           [k
            points
            centroids
            assignments
            history
            domain
            format])


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

(defn generate-k-initial-centroids
  [k-means-state]
  (let [k (:k k-means-state)
        domain (:domain k-means-state)
        column-names (dataset-seq->column-names
                      (read-dataset-seq k-means-state :points))]
    (log/info "Generating initial centroids")
    (ds/->dataset (vec
                   (map #(zipmap column-names %) (repeatedly k #(random-centroid domain)))))))


;;(comment
;;  (def state (initialize-k-means-state "test.csv" 5))
;;  (generate-k-initial-centroids state))



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





;; Initalize k-means state
(defn initialize-k-means-state
  [points-file k]
  (log/info "Initializing k-means state")
  (let [format :parquet
        state (KMeansState.
               k
               points-file
               (change-extension (centroids-filename points-file) :csv)
               (change-extension (assignments-filename points-file) format)
               (change-extension (history-filename points-file) :csv)
               nil
               format)
        new-state (csv-seq-filename->format-seq state :points)]
    (assoc new-state :domain (find-domain new-state))))


;; Read and realize centroids from a file.
(defn read-centroids-from-file
  [k-means-state]
  (ds/rowvecs
   (ds/->dataset
    (:centroids k-means-state) {:file-type :csv :header-row? true})))

;; Compute earth mover distance as distance metric for clustering.
(def distance-fn math/dist-emd)

(defn find-closest-centroid
  [centroids point]
  (let [distances (map (partial distance-fn point) centroids)]
    (first (apply min-key second (map-indexed vector distances)))))

(defn generate-assignments
  [k-means-state]
  (log/info "Generating assignments")
  (let [datasets (read-dataset-seq k-means-state :points)
        columns (dataset-seq->column-names datasets)
        to-vec (fn [row] (map #(get row %) columns))
        assign (comp
                (partial hash-map :assignment)
                (partial
                 find-closest-centroid
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
                 (map (partial map distance-fn)
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




(defn update-history!
  "Adds the latest objective metric to the history file."
  [k-means-state optimization-metric]
  (log/info "Latest objective metric is" optimization-metric)
  (spit (:history k-means-state) (apply str optimization-metric "\n") :append true))

(defn history
  "Return the objective metric history for the given k-means state in chronological order."
  [k-means-state]
  (try
    (ds/rowvecs (ds-csv/csv->dataset (:history k-means-state) {:header-row? false}))
    (catch java.io.FileNotFoundException e  [])))

(defn should-continue-optimizing?
  "Check whether the optimization process has stopped improving."
  [k-means-state]
  (log/info "Checking whether to continue optimizing")
  (let [history (history k-means-state)]
    (and
     ;; Continue optimization if the history is not long enough
     ;; or if the history is not improving.
     (< (count history) 100)
     (or (< (count history) 3)
         (apply not= (take-last 3 history))))))


(defn generate-centroids
  [k-means-state]
  (let [filename (:centroids k-means-state)]
    (ds/write!
     (if (file? filename)
       (update-centroids k-means-state)
       (generate-k-initial-centroids k-means-state))
     filename)))

(defn k-means
  [points-file k]
  (let [k-means-state (as-> (initialize-k-means-state points-file k) state)]
    (log/info "Starting optimization process for" k-means-state)
    (while (should-continue-optimizing? k-means-state)
      (log/info "Starting optimization iteration")
      (generate-centroids k-means-state)
      (generate-assignments k-means-state)
      (update-history! k-means-state (calculate-objective k-means-state)))))
