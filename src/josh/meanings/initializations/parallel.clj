(ns josh.meanings.initializations.parallel
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))


(defmethod initialize-centroids
  :k-means-parallel
  [k-means-state]
  (log/info "Performing k means parallel initialization")
  (let [ds-seq (persist/read-dataset-seq k-means-state :points)
        k (:k k-means-state)
        oversample-factor (* 2 k)
        iterations 5
        rows->maps (partial persist/ds-seq->rows->maps ds-seq)
        k-means (:k-means k-means-state)]
    (loop [i 0 centers (uniform-sample ds-seq 1)]
      (if (= i iterations)
        (do
          (log/info "Finished oversampling. Reducing to k centroids")
          (:centroids (k-means (rows->maps centers) k :init :k-means-++ :distance-fn (:distance-key k-means-state))))
        (recur (inc i) (concat centers
                               (weighted-sample ds-seq
                                                (shortest-distance-squared-* k-means-state centers)
                                                oversample-factor)))))))