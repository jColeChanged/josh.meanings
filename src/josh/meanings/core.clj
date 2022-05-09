(ns josh.meanings.core
  (:require
   [clojure.tools.cli :refer [parse-opts]]
   [josh.meanings.kmeans :refer [k-means csv-filename->arrow-filename csv-seq-filename->arrow-stream file?]])
  (:gen-class))

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
           (let [arrow-filename (csv-filename->arrow-filename filename)]
             (when (not (file? arrow-filename))
               (csv-seq-filename->arrow-stream filename))
             (k-means arrow-filename k)
             0)
           (do
             (println "File" (str filename) "does not exist.")
             1)))))))