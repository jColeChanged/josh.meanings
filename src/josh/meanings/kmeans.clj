(ns josh.meanings.kmeans
  (:require
   [clojure.core.reducers :as reducers]
   [clojure.tools.logging :as log]
   [tech.v3.libs.arrow :as ds-arrow]
   [tech.v3.dataset.reductions :as ds-reduce]
   [tech.v3.dataset :as ds]
   [clojure.string])
  (:use [clojure.data.csv :as csv]
        [clojure.java.io :as io]
        [tech.v3.dataset.math :as ds-math]
        [tech.v3.dataset.io.csv :as ds-csv]
        [fastmath.vector :as math]
        [tech.v3.libs.poi])
  (:gen-class))

(set! *warn-on-reflection* true)

;; k is the number of clusters. 
;; 
;; State is tracked indirectly via files so that we can run 
;; on datasets that are too large to fit in memory. Points, centroids, 
;; assignments, and history are all this type of file references. 
;; 
;; Domain is a vector of mins and a vector maxes for each column.
(defrecord KMeansState [k points centroids assignments history domain])


(defn find-domain [filename]
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
    (ds-arrow/stream->dataset-seq filename))))

;; Generate a random point within the domain
(defn random-between [[min max]]
  (+ (* (rand) (- max min)) min))

(defn random-centroid [domain]
  (zipmap
   (map (partial str "column-") (range))
   (map random-between
        (map vector (first domain) (second domain)))))

(defn generate-k-initial-centroids
  [{:keys [domain k]}]
  (log/info "Generating initial centroids")
  (ds/->dataset (vec (repeatedly k #(random-centroid domain)))))

(comment
  (def state (initialize-k-means-state "test.arrows" 5))
  (generate-k-initial-centroids state))


;; Helper functions related to file handling. 
(def csv-filename->arrow-filename #(clojure.string/replace % #"csv" "arrows"))
(def arrow-filename->csv-filename #(clojure.string/replace % #"arrows?" "csv"))

(defn generate-filename
  [prefix]
  #(str prefix "." %))

(def centroids-filename
  (comp
   arrow-filename->csv-filename
   (generate-filename "centroids")))

(def assignments-filename
  (comp
   csv-filename->arrow-filename
   (generate-filename "assignments")))

(def history-filename
  (comp
   arrow-filename->csv-filename
   (generate-filename "history")))

(defn csv-seq-filename->arrow-stream
  "Converts a csv file into an arrow stream.
   
  CSV isn't a well optimized format for doing large computations.                                                        
  Computing the min and max of each column working with optimized 
  csv seqs can take two orders of magnitude longer than the same 
  operations performed against arrow streams."
  [filename]
  (let [arrow-filename (csv-filename->arrow-filename filename)]
    (log/info "Recieved" filename "which is a csv file")
    (log/info "Converting" filename "to" arrow-filename)
    (ds-arrow/dataset-seq->stream!
     arrow-filename
     {:format :ipc}
     (ds-csv/csv->dataset-seq filename {:header-row? false}))
    (log/info "Conversion completed")))

(defn file?
  "Returns true if a file exists and false otherwise."
  [filename]
  (.exists (clojure.java.io/file filename)))





;; Initalize k-means state
(defn initialize-k-means-state
  [points-file k]
  (KMeansState.
   k
   points-file
   (centroids-filename points-file)
   (assignments-filename points-file)
   (history-filename points-file)
   (find-domain points-file)))


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
  (let [datasets (ds-arrow/stream->dataset-seq (:points k-means-state))
        columns (ds/column-names (first datasets))
        to-vec (fn [row] (map #(get row %) columns))
        assign (comp
                (partial hash-map :assignment)
                (partial
                 find-closest-centroid
                 (read-centroids-from-file k-means-state))
                to-vec)
        map-assignment (fn [dataset] (ds/row-map dataset assign))]
    (ds-arrow/dataset-seq->stream!
     (:assignments k-means-state)
     {:format :ipc}
     (map map-assignment datasets))))


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
                       (ds-arrow/stream->dataset-seq (:assignments k-means-state)))
                      (eduction (map ds/rowvecs) (ds-arrow/stream->dataset-seq (:points k-means-state))))))))

(comment
  (def state (initialize-k-means-state "test.arrow" 5))
  (csv-seq-filename->arrow-stream "test.csv")
  (ds/head (first (ds-arrow/stream->dataset-seq (:points state))))


  (update-centroids state)
  (read-centroids-from-file state)

  (generate-assignments state)

  (ds-arrow/stream->dataset-seq (:assignments state))
  (calculate-objective state))




(defn update-centroids
  [k-means-state]
  (log/info "Recalculating centroids based on assignments")
  (ds/drop-columns
   (ds-reduce/group-by-column-agg
    "assignment"
    (zipmap
     ["column-0" "column-1" "column-2"]
     (map ds-reduce/mean ["column-0" "column-1" "column-2"]))
    (ds-arrow/stream->dataset-seq (:assignments k-means-state)))
   ["assignment"]))



(defn update-history
  "Adds the latest objective metric to the history file."
  [k-means-state optimization-metric]
  (log/info "Latest objective metric is" optimization-metric)
  (spit (:history k-means-state) (apply str optimization-metric "\n") :append true))

(defn history
  "Return the objective metric history for the given k-means state in chronological order."
  [k-means-state]
  (try
    (with-open [reader (io/reader (:history k-means-state))]
      (vec (map (comp #(Double/parseDouble %) first) (csv/read-csv reader))))
    (catch java.io.FileNotFoundException _ [])))


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
  (log/info "Running k-means with k of" (str k) "on file" points-file)
  (let [k-means-state (initialize-k-means-state points-file k)]
    (log/info "Starting optimization process for" k-means-state)
    (while (should-continue-optimizing? k-means-state)
      (log/info "Starting optimization iteration")
      (generate-centroids k-means-state)
      (generate-assignments k-means-state)
      (update-history k-means-state (calculate-objective k-means-state)))))
