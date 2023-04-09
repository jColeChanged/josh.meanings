(ns josh.meanings.persistence
  "A namespace for functions related to reading and writing datasets to various file formats.
  
  This namespace provides functions for loading datasets from files, writing datasets to files,
  and converting between different file formats. It also provides a configuration map of
  supported file formats and their associated reader and writer functions.
  
  Examples:

  (read-dataset-seq state :filename)
   
  (write-dataset-seq state :filename [dataset1 dataset2 ...])"
  (:require [clojure.java.io]
            [clojure.string]
            [clojure.tools.logging :as log]
            [tech.v3.dataset :as ds :refer [select-columns]]
            [tech.v3.dataset.io.csv :as ds-csv]
            [tech.v3.libs.arrow :as ds-arrow]
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
  "Supported file formats for reading and writing datasets."
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
   :csv {:writer (fn [path ds-seq]  
                   (ds/write! (apply ds/concat-copying (first ds-seq) (rest ds-seq)) path))
         :reader ds-csv/csv->dataset-seq
         :suffix ".csv"}})

(defn extension
  "Returns a filenames last file extension."
  [filename]
  (last (clojure.string/split filename #"\.")))

(defn filename->format
  "Parses a format lookup key from a filename.
  
  The `filename` argument should be a string containing the filename to parse. It 
  must contain a file extension that is in formats.
  
  Returns the format lookup key for the given filename."
  [^String filename]
  {:pre [(string? filename)]
   :post [(contains? formats %)]}
  (-> filename extension keyword))

(defn file?
  "Returns true if a file exists and false otherwise."
  [filename]
  (.exists (clojure.java.io/file filename)))


(defn select-columns-seq
  "Selects columns from each dataset in the sequence.

  The `datasets-seq` argument should be a sequence of datasets. The `col-names`
  argument should be a collection of column names to select from each dataset.

  Returns a sequence of datasets containing the specified columns."
  [^clojure.lang.Seqable datasets-seq col-names]
  (map (fn [ds] (ds/select-columns ds col-names)) datasets-seq))


(defn read-dataset-seq
  "Loads the dataset at the file path in key.
   
  If the `filename` argument is provided, it should be a string containing the
  file path of the dataset to load. If the `s` and `key` arguments are provided,
  the `s` argument should be a map containing the `key` value that identifies
  the file path of the dataset to load.
  
  Returns a sequence of datasets from the specified file.
   
  Examples:
    
  (read-dataset-seq '/path/to/file.csv')
  (read-dataset-seq {:filename '/path/to/file.csv'} :filename)"
  ([]
   (throw (IllegalArgumentException. "Missing required argument: filename")))
  ([^String filename] 
   {:post [(seq? %) (every? ds/dataset? %)]}
   (let [format (filename->format filename)
         reader-fn (-> formats format :reader)]
     (log/info "Loading" filename "with" format)
     (reader-fn filename)))
  ([^clojure.lang.IPersistentMap s ^clojure.lang.Keyword key]
   {:pre [(map? s) (keyword? key)]} 
   (if (= key :points)
     (select-columns-seq (read-dataset-seq (key s)) (:col-names s))
     (read-dataset-seq (key s)))))

(defn dataset-seq->column-names
  "Returns a sequence of column names for a given dataset sequence.
  
  The `ds-seq` argument should be a sequence of datasets for which to
  return the column names. The returned sequence will contain the column
  names for the first dataset in the sequence.
  
  Returns a sequence of column names for the given dataset sequence."
  [ds-seq]
  (ds/column-names (first ds-seq)))


(defn write-dataset
  "Writes a dataset to a file using the specified format.
  
  The `dataset` argument should be a sequence of datasets to write to the file. 
  The file format will be determined by the file extension of the filename.
  
  Returns nil."
  ([filename dataset]
   (log/info "About to write" filename "with data" dataset)
   (ds/write! dataset filename)
   (log/info "Finished writing " filename))
  ([s key dataset]
   {:pre [(map? s) (keyword? key) (ds/dataset? dataset)]}
   (write-dataset (key s) dataset)))

(defn write-datasets
  "Writes a sequence of datasets to filename.
  
  The `ds-seq` argument should be a sequence of datasets to write to the file. 
  The file format will be determined by the file extension of the filename.
   
  Returns nil."
  [filename ds-seq]
  (let [format (filename->format filename)
        writer-fn! (-> formats format :writer)]
    (log/info "About to write" filename)
    (writer-fn! filename ds-seq)
    (log/info "Finished writing " filename)))

(defn write-dataset-seq
  "Writes a sequence of datasets to a file using the specified format.
  
  The `k-means-state` argument should be a map containing the `key` value that
  identifies the file to write to. The `ds-seq` argument should be a sequence of
  datasets to write to the file. The file format will be determined by the file
  extension of the filename specified in the `k-means-state` map.
  
  Returns nil."
  [k-means-state key ds-seq]
  (write-datasets (key k-means-state) ds-seq))


(defn change-extension
  [filename format]
  (let [desired-suffix (:suffix (formats format))]
    (clojure.string/replace filename #"(.*)\.(.*?)$" (str "$1" desired-suffix))))




(defn convert-file
  "Converts a file into another file type and returns the new filename."
  [filename format]
  (let [new-filename (change-extension filename format)
        reader-fn (:reader (formats (filename->format filename)))]
    (when (not= filename new-filename)
      (log/info "Converting" filename "to" new-filename)
      (write-datasets new-filename (reader-fn filename))
      (log/info "Conversion completed"))
    new-filename))



(defn ds-seq->rows->maps
  [ds-seq rows]
  (let [column-names (dataset-seq->column-names ds-seq)]
    (map #(zipmap column-names %) rows)))

(defn generate-filename
  [prefix]
  #(str prefix "." %))

(def centroids-filename (generate-filename "centroids"))

(def assignments-filename (generate-filename "assignments"))
