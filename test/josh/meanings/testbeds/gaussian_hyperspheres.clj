(ns josh.meanings.testbeds.gaussian-hyperspheres
  (:require [fastmath.random :as random]
            [tech.v3.dataset :as ds]))

(defn gen-from-hypersphere
  "Returns a sample from gaussian distribution centered 
  on the point that is provided."
  [center]
  (map random/sample (map #(random/distribution :normal {:mu % :sd 50}) center)))

(defn gen-hypersphere
  "Returns a centroid for a hypersphere with dim-count dimensions."
  [dim-count]
  (let [distribution (random/distribution :normal {:mu 0 :sd 1000})]
    (repeatedly dim-count #(random/sample distribution))))

(defn gen-dataset-points
  "Returns a sequence of points sampled from guassian hyperspheres."
  [cluster-count dim-count]
  (shuffle
   (let [hyperspheres (repeatedly cluster-count (partial gen-hypersphere dim-count))]
     (mapcat #(repeatedly 100 (partial gen-from-hypersphere %)) hyperspheres))))

(defn gen-dataset
  "Returns a dataset of points sampled from guassian hyperspheres."
  [cluster-count dim-count]
  (ds/->dataset
   (map (partial zipmap (range dim-count))
        (gen-dataset-points cluster-count dim-count))))