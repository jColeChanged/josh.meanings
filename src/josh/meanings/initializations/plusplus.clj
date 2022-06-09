(ns josh.meanings.initializations.plusplus
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist]
   [tech.v3.dataset :as ds])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))



(defmethod initialize-centroids
  :k-means-++
  [k-means-state]
  (log/info "Performing k means++ initialization")
  (let [ds-seq (persist/read-dataset-seq k-means-state :points)
        k (:k k-means-state)
        rows->maps (partial persist/ds-seq->rows->maps ds-seq)]
    (loop [centers (uniform-sample ds-seq 1)]
      (if (= k (count centers))
        (ds/->dataset (rows->maps centers))
        (recur (concat centers
                       (weighted-sample ds-seq
                                        (shortest-distance-squared-* k-means-state centers)
                                        1)))))))