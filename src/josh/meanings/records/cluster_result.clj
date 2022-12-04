(ns josh.meanings.records.cluster-result
  (:require
   [josh.meanings.protocols.cluster-model :refer [PClusterModel]]
   [tech.v3.dataset :as ds]))


(defrecord ClusterResult
  [centroids      ;; A vector of points
   cost           ;; The total distance between centroids and assignments
   configuration  ;; a map of details about the configuration used to generate the cluster result
   ]
  
  PClusterModel 
  
  (save-model
   [this filename]
   (spit filename (pr-str this)))
  
  (load-assignments
   [this]
   (ds/->dataset (:assignments (:configuration this))))
  
  (classify
   [this point]
   (apply min-key (map (partial (:distance-fn point)) (:centroids this)))))