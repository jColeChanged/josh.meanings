(ns josh.meanings.records.cluster-result
  "Cluster results are the result of a clustering operationg.  They contain 
   a reference to the models centroids, the assignments which generated those 
   centroids, the objective function cost of the clustering, the format that 
   the cluster is saved in, and some configuration details about how the clustering 
   process was run."
  (:require
   [clojure.spec.alpha :as s]
   [josh.meanings.protocols.savable :refer [Savable]]
   [josh.meanings.specs]
   [tech.v3.dataset :as ds]))


(defrecord ClusterResult 
  [centroids      ;; The filename of the centroids dataset
   cost           ;; The total distance between centroids and assignments
   configuration  ;; a map of details about the configuration used to generate the cluster result
   ])

(s/def :cluster-result/centroids :josh.meanings.specs/dataset)
(s/def :cluster-result/cost :josh.meanings.specs/number)
(s/def :cluster-result/configuration 
  (s/keys
   :req-un [:josh.meanings.specs/k 
            :josh.meanings.specs/m 
            :josh.meanings.specs/distance-key 
            :josh.meanings.specs/init 
            :josh.meanings.specs/col-names]))

(s/def ::cluster-result
  (s/keys
   :req-un [:cluster-result/centroids
            :cluster-result/cost
            :cluster-result/configuration]))


(extend-type ClusterResult
  Savable
  (save-model [this filename]
    (spit filename (pr-str (-> this
                               (update :centroids ds/rows)
                               (update-in [:configuration :centroids] ds/rows))))))


(s/fdef load-model
  :args (s/cat :filename :josh.meanings.specs/filename)
  :ret ::cluster-result)
(defn load-model
  [filename]
  (-> filename 
      slurp 
      read-string
      (update :centroids ds/->dataset)
      (update-in [:configuration :centroids] ds/->dataset)))

