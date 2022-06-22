(ns josh.meanings.initializations.plusplus
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist]
   [clojure.spec.alpha :as s]
   [josh.meanings.initializations.utils :refer
    [centroids->dataset uniform-sample weighted-sample shortest-distance-squared-*]])
  (:use
   [josh.meanings.initializations.core]))

(def t-config :josh.meanings.specs/configuration)
(def t-dataset :josh.meanings.specs/dataset)

(s/fdef k-means-++ :args (s/cat :config t-config) :ret t-dataset)
(defn- k-means-++
  [config]
  (log/info "Performing k means++ initialization")
  (centroids->dataset
   config
   (loop [centers (uniform-sample (persist/read-dataset-seq config :points) 1)]
     (if (= (:k config) (count centers))
       centers
       (recur (concat centers
                      (weighted-sample (persist/read-dataset-seq config :points)
                                       (shortest-distance-squared-* config centers)
                                       1)))))))

(defmethod initialize-centroids
  :k-means-++
  [k-means-state]
  (k-means-++ k-means-state))