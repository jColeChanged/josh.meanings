(ns josh.meanings.records.clustering-state
  "Provides a defrecord for storing the configuration of a clustering process 
   and a protocol for retreiving stateful IO like the potentially larger than 
   memory points and assignments datasets."
  (:require
   [josh.meanings.persistence :as persist]
   [tech.v3.dataset :as ds]))


(defrecord KMeansState
           [k                 ;; Number of clusters 
            points            ;; filename of the points dataset
            format            ;; The format that will be used to store the points, centroids and assignments.
            init              ;; The initialization method that will be used to generate the initial centroids.
            distance-key      ;; The key that will be used to determine the distance function to use.
            m                 ;; The chain length to use when doing monte carlo sampling if applicable.
            k-means           ;; A reference to the k-means function; sometimes k means classification requires recursion.
            size-estimate     ;; An estimate of the size of the dataset.  Sometimes useful in initialization methods and sanity checks. 
            col-names         ;; The column names of the dataset used for clustering.
            use-gpu           ;; Whether to use GPU or not.
            ])
