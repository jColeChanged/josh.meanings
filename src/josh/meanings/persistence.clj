(ns josh.meanings.persistence
  (:require
   [clojure.tools.logging :as log]
   [clojure.string]
   [clojure.java.io]
   [tech.v3.dataset :as ds]
   [tech.v3.libs.arrow :as ds-arrow]
   [tech.v3.dataset.io.csv :as ds-csv]
   [tech.v3.libs.parquet :as ds-parquet])
  (:gen-class))

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
   :csv {:writer (fn [path ds-seq] (ds/write! ds-seq path))
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
    (reader-fn filename {:key-fn keyword})))

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

(defn ds-seq->rows->maps
  [ds-seq rows]
  (let [column-names (dataset-seq->column-names ds-seq)]
    (map #(zipmap column-names %) rows)))

(defn file?
  "Returns true if a file exists and false otherwise."
  [filename]
  (.exists (clojure.java.io/file filename)))




(defn generate-filename
  [prefix]
  #(str prefix "." %))

(def centroids-filename (generate-filename "centroids"))

(def assignments-filename (generate-filename "assignments"))


;; Read and realize centroids from a file.
(defn read-centroids-from-file
  [k-means-state]
  (ds/rowvecs
   (ds/->dataset
    (:centroids k-means-state) {:key-fn keyword :file-type :csv :header-row? true})))