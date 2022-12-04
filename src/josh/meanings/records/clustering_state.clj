(ns josh.meanings.records.clustering-state
  "Provides a defrecord for storing the configuration of a clustering process 
   and a protocol for retreiving stateful IO like the potentially larger than 
   memory points and assignments datasets."
  (:require
   [josh.meanings.protocols.clustering-state :refer [PClusteringState]]
   [josh.meanings.persistence :as persist]
   [tech.v3.dataset :as ds]))


(defrecord KMeansState
  [k                 ;; Number of clusters 
   points            ;; filename of the points dataset
   centroids         ;; filename of the centroids dataset
   assignments       ;; filename of the assignments dataset
   format            ;; The format that will be used to store the points, centroids and assignments.
   init              ;; The initialization method that will be used to generate the initial centroids.
   distance-key      ;; The key that will be used to determine the distance function to use.
   distance-fn       ;; The distance function itself, derived from the distance-key.
   m                 ;; The chain length to use when doing monte carlo sampling if applicable.
   k-means           ;; A reference to the k-means function; sometimes k means classification requires recursion.
   size-estimate     ;; An estimate of the size of the dataset.  Sometimes useful in initialization methods and sanity checks. 
   ]

  PClusteringState
  
  (load-centroids
    [this]
    (-> (:centroids this)
        (ds/->dataset {:header-row? true})))

  (load-points
    [this]
    (persist/read-dataset-seq this :points))

  (load-assignments
    [this]
    (persist/read-dataset-seq this :points))

  (column-names
    [this]
    (remove #{"assignments" "q(x)"}
            (-> this
                (persist/read-dataset-seq :points)
                first
                (ds/column-names)))))