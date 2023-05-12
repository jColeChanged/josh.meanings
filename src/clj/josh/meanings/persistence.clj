(ns josh.meanings.persistence
  "A namespace for functions related to reading and writing datasets to various file formats.
  
  This namespace provides functions for loading datasets from files, writing datasets to files,
  and converting between different file formats. It also provides a configuration map of
  supported file formats and their associated reader and writer functions.
  
  Examples:

  (read-dataset-seq state :filename)"
  (:require [clojure.java.io]
            [clojure.string]
            [tech.v3.dataset :as ds]
            [ham-fisted.lazy-noncaching :as nfln]
            [tech.v3.dataset.io.csv :as ds-csv]
            [tech.v3.libs.arrow :as ds-arrow]
            [tech.v3.libs.parquet :as ds-parquet]
            [clojure.spec.alpha :as s]))

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
    :reader (fn [path]
              (ds-arrow/stream->dataset-seq path {:open-type :mmap}))
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

(s/fdef extension :args (s/cat :filename string?) :ret string?)
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
  [ds-seq col-names]
  (nfln/map (fn [ds] (ds/select-columns ds col-names)) ds-seq))



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
   (let [format (filename->format filename)
         reader-fn (-> formats format :reader)]
     (reader-fn filename)))
  ([^clojure.lang.IPersistentMap s ^clojure.lang.Keyword key]
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
   (ds/write! dataset filename))
  ([s key dataset]
   (write-dataset (key s) dataset)))

(defn write-datasets
  "Writes a sequence of datasets to filename.
  
  The `ds-seq` argument should be a sequence of datasets to write to the file. 
  The file format will be determined by the file extension of the filename.
   
  Returns nil."
  [filename ds-seq]
  (let [format (filename->format filename)
        writer-fn! (-> formats format :writer)]
    (writer-fn! filename ds-seq)))

(def write-dataset-seq write-datasets)


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
      (write-datasets new-filename (reader-fn filename)))
    new-filename))


(defn ds-seq->rows->maps
  [ds-seq rows]
  (let [column-names (dataset-seq->column-names ds-seq)]
    (map #(zipmap column-names %) rows)))
