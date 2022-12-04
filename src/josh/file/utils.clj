(ns josh.file.utils
  (:require
   [clojure.string :as string]
   [clojure.java.io :as io]))

(defn extension
  "Returns a filenames last file extension."
  [filename]
  (last (string/split filename #"\.")))

(defn file?
  "Returns true if a file exists and false otherwise."
  [filename]
  (.exists (io/file filename)))  