(ns josh.meanings.testbeds.gaussian-hyperspheres
  (:require [fastmath.random :as random]))


(defn gen-from-hypersphere
  [center]
  (map random/sample (map #(random/distribution :normal {:mu % :sd 50}) center)))

(defn gen-hypersphere
  [dim-count]
  (let [distribution (random/distribution :normal {:mu 0 :sd 1000})]
    (repeatedly dim-count #(random/sample distribution))))

(defn gen-dataset
  [cluster-count dim-count]
  (let [hyperspheres (repeatedly cluster-count (partial gen-hypersphere dim-count))]
    (mapcat #(repeatedly 1000 (partial gen-from-hypersphere %)) hyperspheres)))