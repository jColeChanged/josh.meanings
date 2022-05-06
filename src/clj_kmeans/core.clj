(ns clj-kmeans.core
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [clojure.core.reducers :as reducers]
   [tech.v3.libs.arrow :as ds-arrow]
   [tech.v3.dataset :as ds])
  (:use [clojure.data.csv :as csv]
        [clojure.java.io :as io]
        [tech.v3.dataset.math :as ds-math]
        [tech.v3.dataset.io.csv :as ds-csv]
        [fastmath.vector :as math]
        [tech.v3.libs.poi])
  (:gen-class))


(set! *warn-on-reflection* true)


;; Program state is tracked indirectly via files so that we can run 
;; on datasets that are too large to fit in memory. Points, centroids, 
;; assignments, and history are all this type of file references. 
;; 
;; k is a parameter that controls the number of clusters. Both it and 
;; the points filename are passed into the program as command line 
;; arguments. Domain is the valid domain for each point axis. It is not 
;; a file reference, but a vector [min, max] for each axis of points.
(defrecord KMeansState [k points centroids assignments history domain])


(defn load-dataset-from-file
  [filename]
  (ds-csv/csv->dataset-seq filename {:header-row? false :file-type :csv}))


(defn find-domain [filename]
  (println "Finding the domain...")
  (reducers/fold
   (fn
     ([] [(repeat 1000) (repeat -1000)])
     ([x y]
      [(math/emn (first x) (first y))
       (math/emx (second x) (second y))]))
   (eduction
    (comp
     (map #(ds/brief % {:stat-names [:min :max]}))
     (map (juxt
           (partial map :min)
           (partial map :max))))
    (load-dataset-from-file filename))))




;; Generate a random point within the domain
(defn random-between [[min max]]
  (+ (* (rand) (- max min)) min))

(defn random-centroid [domain] (map random-between domain))

(defn generate-k-initial-centroids
  [{:keys [domain k]}]
  (println "Generating initial centroids.")
  (vec (repeatedly k #(random-centroid domain))))

;; Helper functions to generate filenames for centroids, assignments, 
;; and history.
(defn generate-filename [prefix] #(str prefix "." %))
(def centroids-filename (generate-filename "centroids"))
(def assignments-filename (generate-filename "assignments"))
(def history-filename (generate-filename "history"))

;; Initalize k-means state
(defn initialize-k-means-state
  [points-file k]
  (prn
   (KMeansState.
    k
    points-file
    (centroids-filename points-file)
    (assignments-filename points-file)
    (history-filename points-file)
    (find-domain points-file))))


;; Read and realize centroids from a file.
(defn read-centroids-from-file
  [k-means-state]
  (ds/rowvecs
   (ds/->dataset
    (:centroids k-means-state) {:file-type :csv :header-row? false})))

;; Compute earth mover distance as distance metric for clustering.
(def distance-fn math/dist-emd)

(defn find-closest-centroid
  [centroids point]
  (let [distances (map (partial distance-fn point) centroids)]
    (first (apply min-key second (map-indexed vector distances)))))


(defn generate-assignments
  [k-means-state]
  (println "Generating assignments.")
  (let [dataset (ds-csv/csv->dataset-seq (:points k-means-state) {:header-row? false :file-type :csv})
        columns (ds/column-names (first dataset))
        to-vec (fn [row] (map #(get row %) columns))
        assign (comp
                (partial hash-map :assignment)
                (partial
                 find-closest-centroid
                 (read-centroids-from-file k-means-state))
                to-vec)
        map-assignment (comp
                        (map #(ds/row-map % assign))
                        (map #(ds/select-columns % [:assignment])))]
    (ds-arrow/dataset-seq->stream!
     (:assignments k-means-state)
     (eduction map-assignment dataset))))


;; "Elapsed time: 33741.2376 msecs"
(defn calculate-objective
  [k-means-state]
  (println "Calculating objective.")
  (let [centroids (read-centroids-from-file k-means-state)
        assignments (ds/->dataset (:assignments k-means-state) {:header-row? false :file-type :csv})
        points (ds/->dataset (:points k-means-state) {:header-row? false :file-type :csv})
        assigned-centroids (map #(nth centroids (first %)) (ds/rowvecs assignments))]
    (reduce + 0 (map distance-fn assigned-centroids (ds/rowvecs points)))))


;; Centroids in k-means clustering are computed by finding the center of 
;; the points assigned to each centroid. This is done by averaging the 
;; points assigned to each centroid. With k points, that is k means, 
;; and thus the name of the algorithm.
;; 
;; In order to compute the centers of the distrubtions we use a map 
;; reduce approach. Assignments are integers between 0 and k-1. So we 
;; can track each centroid as an index into a vector of k centroids.
;;
;; Vector will track the sum of the points in the cluster. Count will 
;; track the number of elements in the cluster.
(defrecord MRMean [vector count])

(defn means
  "Creates a vector of k distributions, each with m dimensions."
  [k m]
  (vec (repeatedly k #(MRMean. (vec (repeat m 0.0)) 0))))


(defn add-mr-mean
  [mr-mean-1 mr-mean-2]
  (-> mr-mean-1
      (update-in [:count] (fn [count] (+ count (:count mr-mean-2))))
      (update-in [:vector] (fn [vector] (math/add vector (:vector mr-mean-2))))))

(defn mr-mean-combiner
  [mr-means-1 mr-means-2]
  (map add-mr-mean mr-means-1 mr-means-2))

(defn mr-mean-reducer
  "Update MRMean according to a point's assignment."
  [mr-mean [assignment point]]
  (-> mr-mean
      (update-in [assignment :count] inc)
      (update-in [assignment :vector] (fn [vector] (math/add vector point)))))



(defn mean
  "Find the mean for a kr-mean."
  [mr-mean]
  (math/div (:vector mr-mean) (:count mr-mean)))

(defn new-centroid
  "Returns the new centroid computed as a function of kr-mean and the domain."
  [domain kr-mean]
  (let [count (:count kr-mean)]
    (if (zero? count)
      (random-centroid domain)
      (mean kr-mean))))


(defn update-centroids
  [k-means-state]
  (println "Reclalcuating centroid centers based on assignments")
  (let [domain (:domain k-means-state)
        m (count (first domain))
        k (:k k-means-state)
        assignments-dataset (ds-arrow/stream->dataset-seq (:assignments k-means-state))
        points-dataset (load-dataset-from-file (:points k-means-state))]
    (map
     (partial new-centroid domain)

     (reduce mr-mean-combiner
             (map
              (comp
               (partial reduce mr-mean-reducer (means k m))
               (partial map vector))
              (eduction
               (comp
                (map ds/rowvecs)
                (map (partial map first)))
               assignments-dataset)
              (eduction
               (map ds/rowvecs)
               points-dataset))))))

(defn update-history
  "Adds the latest objective metric to the history file."
  [k-means-state optimization-metric]
  (println "Latest objective metric is" optimization-metric)
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
  (println "Checking whether to continue optimizing.")
  (let [history (history k-means-state)]
    (and
     ;; Continue optimization if the history is not long enough
     ;; or if the history is not improving.
     (< (count history) 100)
     (or (< (count history) 3)
         (apply not= (take-last 3 history))))))



(defn file?
  [filename]
  (.exists (clojure.java.io/file filename)))


(defn write-centroids [filename centroids]
  (with-open [writer (io/writer filename)]
    (csv/write-csv writer centroids)))

(defn generate-centroids
  [k-means-state]
  (write-centroids
   (:centroids k-means-state)
   (if (file? (:centroids k-means-state))
     (update-centroids k-means-state)
     (generate-k-initial-centroids k-means-state))))


(defn k-means
  [points-file k]
  (let [k-means-state (initialize-k-means-state points-file k)]
    (println "Starting optimization process for" k-means-state)
    (while (should-continue-optimizing? k-means-state)
      (println "Starting optimization iteration.")
      (generate-centroids k-means-state)
      (generate-assignments k-means-state)
      (update-history k-means-state (calculate-objective k-means-state)))))


(defn parse-integer [#^String x] (Integer. x))

(def cli-options
  [[nil "--input File containing points to categorize."
    :id :filename]
   [nil "--k Number of clusters."
    :id :k
    :parse-fn parse-integer]
   ["-h" "--help"]])

(defn print-usage-instructions
  [options]
  (println "Usage: k-means --input <filename.csv> --k <k>\n")
  (println (:summary options))
  (println)
  (doall (map println (:errors options))))

(defn -main
  [& args]

  (System/exit
   (let [options (parse-opts args cli-options)]
     (if (or (:help options) (:errors options))
       (do
         (print-usage-instructions options)
         (System/exit 1))
       (let [filename (-> options :options :filename)
             k (-> options :options :k)]
         (if (file? filename)
           (do
             (println "Running k-means with k of" (str k) "on file" (str filename))
             (k-means filename k)
             0)
           (do
             (println "File" (str filename) "does not exist.")
             1)))))))



