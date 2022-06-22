(ns josh.meanings.initializations.parallel
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as persist]
   [clojure.spec.alpha :as s]
   [josh.meanings.initializations.utils :refer
    [uniform-sample weighted-sample shortest-distance-squared-*]])
  (:use
   [josh.meanings.initializations.core]))

(def t-config :josh.meanings.specs/configuration)
(def t-dataset :josh.meanings.specs/dataset)
(s/fdef k-means-parallel :args (s/cat :config t-config) :ret t-dataset)
(defn k-means-parallel
  [config]
  (log/info "Performing k means parallel initialization")
  (let [ds-seq (persist/read-dataset-seq config :points)
        k (:k config)
        oversample-factor (* 2 k)
        iterations 5
        rows->maps (partial persist/ds-seq->rows->maps ds-seq)
        k-means (:k-means config)]
    (loop [i 0 centers (uniform-sample ds-seq 1)]
      (if (= i iterations)
        (do
          (log/info "Finished oversampling. Reducing to k centroids")
          (:centroids (k-means (rows->maps centers) k :init :k-means-++ :distance-fn (:distance-key config))))
        (recur (inc i) (concat centers
                               (weighted-sample ds-seq
                                                (shortest-distance-squared-* config centers)
                                                oversample-factor)))))))


(defmethod initialize-centroids
  :k-means-parallel
  [config]
  (k-means-parallel config))