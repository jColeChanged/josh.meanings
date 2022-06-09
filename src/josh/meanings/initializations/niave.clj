(ns josh.meanings.initializations.niave
  "A random initialization strategy for k means which lacks theoretical 
   guarantees on solution quality for any individual run, but which will 
   complete in O(n + k*d) time and only takes O(k*d) space."
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))

(defn- niave-initialization
  [k-means-state]
  (log/info "Performing classical (naive) k means initialization")
  (uniform-sample (persist/read-dataset-seq k-means-state :points) (:k k-means-state)))

(defmethod initialize-centroids
  :niave
  [k-means-state]
  (centroids->dataset k-means-state (niave-initialization k-means-state)))