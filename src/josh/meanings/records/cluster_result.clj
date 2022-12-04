(ns josh.meanings.records.cluster-result
  "Cluster results are the result of a clustering operationg.  They contain 
   a reference to the models centroids, the assignments which generated those 
   centroids, the objective function cost of the clustering, the format that 
   the cluster is saved in, and some configuration details about how the clustering 
   process was run.
   
   Callers who wish to use the cluster result can get access to the centroids with:
   
   ```
   (.load-centroids cluster-result)
   ```
   
   They can get access to the assignments with:
   
   ```
   (.load-assignments-datasets cluster-result)
   ```
   
   They can also cluster points with the cluster result with:
   
   ```
   (.classify cluster-result x)
   ```
   
   Where x is a vector of points in `(-> cluster-result :configuration :col-names)` order or a map
   that contains every col-name as a key.
   
   To save a cluster result to disk
   
   ```
   (.save-model cluster-result filename)
   ```
   
   To load a cluster result from disk
   
   ```
   (load-model filename)
   ```
   "
  (:require
   [josh.meanings.persistence :refer [read-dataset-seq]]
   [josh.meanings.distances :refer [get-distance-fn]]
   [josh.meanings.protocols.savable :refer [Savable]]
   [josh.meanings.protocols.classifier :refer [Classifier load-centroids]]
   [josh.meanings.classify :as classification-utils]
   [josh.meanings.specs]
   [clojure.spec.alpha :as s]
   [tech.v3.dataset :as ds]))


(defrecord ClusterResult 
  [centroids      ;; The filename of the centroids dataset
   assignments    ;; The filename of the assignments dataset 
   format         ;; The format that the datasets are saved in.
   cost           ;; The total distance between centroids and assignments
   configuration  ;; a map of details about the configuration used to generate the cluster result
   ])

(s/def :cluster-result/centroids :josh.meanings.specs/filename)
(s/def :cluster-result/assignments :josh.meanings.specs/filename)
(s/def :cluster-result/format :josh.meanings.specs/format-key)
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
            :cluster-result/assignments
            :cluster-result/format
            :cluster-result/cost
            :cluster-result/configuration]))


(defn load-assignments-impl
  "Loads the assignments dataset sequence from disk."
  [^ClusterResult this]
  (read-dataset-seq this :assignments)) 

(s/fdef load-assignments-impl
  :args (s/cat :this ::cluster-result)
  :ret (s/coll-of :josh.meanings.specs/dataset-seq))


(defn load-centroids-impl
  "Loads the centroids dataset sequence from disk."
  [^ClusterResult this]
  (-> this :centroids ds/->dataset))

(s/fdef load-centroids-impl
  :args (s/cat :this ::cluster-result)
  :ret :josh.meanings.specs/dataset)


(defmulti classify-impl
  "Classifies the specified point using the cluster result.
    
  The input must be either a vector of points in the order specified by the
  `col-names` configuration field, or a map with keys corresponding to the 
  `col-names` field.
  
  Returns the index of the centroid that is closest to the point.

  Example usage:
  
  (def cluster-result (load-model 'cluster-result.edn'))
  (def col-names (:col-names (:configuration cluster-result)))
    
  ;; classify a point vector
  (classify cluster-result [5.1 3.5 1.4 0.2])
    
  ;; classify a point map
  (classify cluster-result (zipmap col-names [5.1 3.5 1.4 0.2]))"
  (fn [_ x] (if (map? x) :map :vector)))




(defn save-model-impl
  "Saves the cluster result to disk.  Returns the filename that the cluster result 
   was saved to.
   
   Example usage:
   
   (save-model cluster-result 'cluster-result.edn')
   "
  [^ClusterResult this ^String filename]
  (spit filename (pr-str this))
  filename)



(s/fdef save-model-impl
  :args (s/cat :this ::cluster-result
               :filename :josh.meanings.specs/filename)
  :ret :josh.meanings.specs/filename)



(extend-type ClusterResult
  Savable
  (save-model 
    [^ClusterResult this ^String filename] 
    (save-model-impl this filename)))

(extend-type ClusterResult
  Classifier
  (load-centroids [this]
    (load-centroids-impl this))
  
  (load-assignments
   [^ClusterResult this]
   (load-assignments-impl this))
  
  (classify
  [^ClusterResult this x]
  (classify-impl this x)))






(defn load-model
  "Loads a `ClusterResult` from the specified `filename`. Returns the `ClusterResult`.
   
   __Example Usage__
   
   To load a model from disk, first you need to save a model. This can be done 
   with the `save-model` method:
   
   `(.save-model (k-mean 'dataset.csv' 5) 'cluster-result.edn')`
   
   Then you can load that model from with the load-model function:
   
   `(load-model 'cluster-result.edn')`
   "
  [^String filename]
  (-> filename 
      slurp 
      read-string))

(s/fdef load-model
  :args (s/cat :filename :josh.meanings.specs/filename)
  :ret ::cluster-result)


(defn classify-map
  [^ClusterResult this x]
  (let [point (vec (for [col (-> this :configuration :col-names)] (x col)))]
    (classify-impl this point)))

(s/fdef classify-map
  :args (s/cat 
         :this ::cluster-result 
         :x map?)
  :ret nat-int?
  :fn #(let [this (-> % :args :this)
             x (-> % :args :x)]
          (and
           (s/valid? :josh.meanings.specs/col-names (keys x))
           (every? (set (-> this :configuration :col-names)) (keys x)))))

(defn classify-vector
  [this point]
  (let [distance-fn (get-distance-fn (-> this :configuration :distance-key))
        centroids (-> (load-centroids this)
                      (ds/select-columns (-> this :configuration :col-names))
                      (ds/rowvecs))]
    (println "About to classify with" centroids point)
    (classification-utils/classify centroids distance-fn point)))

(defmethod classify-impl :map
  [this x]
  (classify-map this x))

(defmethod classify-impl :vector
  [this x]
  (classify-vector this x))