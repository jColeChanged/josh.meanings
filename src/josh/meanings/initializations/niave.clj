(ns josh.meanings.initializations.niave
  "A random initialization strategy for k means which lacks theoretical 
   guarantees on solution quality for any individual run, but which will 
   complete in O(n + k*d) time and only takes O(k*d) space."
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist]
   [tech.v3.dataset :as ds])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))

(defmethod initialize-centroids
  :niave
  [k-means-state]
  (log/info "Performing classical (naive) k means initialization")
  (let [k (:k k-means-state)
        rows->maps (partial persist/ds-seq->rows->maps (persist/read-dataset-seq k-means-state :points))]
    (ds/->dataset (rows->maps (uniform-sample (persist/read-dataset-seq k-means-state :points) k)))))