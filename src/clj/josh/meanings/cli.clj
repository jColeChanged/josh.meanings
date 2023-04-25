(ns josh.meanings.cli
  (:require [clojure.tools.cli :refer [parse-opts]]
            [josh.meanings.distances :as distances]
            [josh.meanings.kmeans :refer [initialize-k-means-state lloyd]]
            [tech.v3.dataset :as ds])
  (:gen-class))

(def cli-options
  [["-k" "--cluster-count K" "Number of clusters" :parse-fn #(Integer/parseInt %)]
   ["-f" "--file NAME" "File to perform clustering on"]
   ["-c" "--columns column name" "Columns to cluster"
    :multi true
    :default []
    :update-fn conj]])

(defn -main [& args]
  (let [parsed (parse-opts args cli-options)
        options (:options parsed)
        file (:file options)
        k (:cluster-count parsed)
        columns (:columns parsed)]
    (println (:summary parsed))
    (println parsed)
    (let [conf (initialize-k-means-state file k {:columns columns})]
      (distances/with-gpu-context conf
        (ds/write! (lloyd conf) "centroids.arrow")))))

(when *compile-files*
  (shutdown-agents))