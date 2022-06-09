(ns josh.meanings.initializations.plusplus
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))


(defn- k-means-++-initialization
  [k-means-state]
  (log/info "Performing k means++ initialization")
  (loop [centers (uniform-sample (persist/read-dataset-seq k-means-state :points) 1)]
    (if (= (:k k-means-state) (count centers))
      centers
      (recur (concat centers
                     (weighted-sample (persist/read-dataset-seq k-means-state :points)
                                      (shortest-distance-squared-* k-means-state centers)
                                      1))))))

(defmethod initialize-centroids
  :k-means-++
  [k-means-state]
  (centroids->dataset k-means-state (k-means-++-initialization k-means-state)))