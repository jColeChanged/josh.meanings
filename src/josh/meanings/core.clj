(ns josh.meanings.core
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [josh.meanings.kmeans :refer [k-means csv-filename->arrow-filename csv-seq-filename->arrow-stream file?]])
  (:gen-class))


(defn print-usage-instructions
  [options]
  (println "Usage: k-means --input <filename.csv> --k <k>\n")
  (println (:summary options))
  (println)
  (doall
   (map println (:errors options)))
  1)

(defn call-k-means
  [options]
  (let [filename (:filename (:options options))
        k (:k (:options options))]
    (k-means filename k)
    0))

(def cli-options
  [["-k" "--k k-clusters" "Cluster count"
    :default 5
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 1 % 0x10000) "Must be a number between 1 and 65536"]]
   ["-i" "--input input" "Input file"
    :id :filename
    :parse-fn str
    :validate [file? "Must be a file"]]
   ["-h" "--help"]])


(defn -main [& args]
  (System/exit
   (let [options (parse-opts args cli-options)]
     (if (or (:errors options) (:help options))
       (print-usage-instructions options)
       (call-k-means options)))))