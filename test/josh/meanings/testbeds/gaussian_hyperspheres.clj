(ns josh.meanings.testbeds.gaussian-hyperspheres
  (:require [fastmath.random :as random]
            [clojure.spec.alpha :as s]))


(s/fdef gen-from-hypersphere
  :args [:josh.meanings.specs/point]
  :ret :josh.meanings.specs/point)
(defn gen-from-hypersphere
  "Returns a sample from gaussian distribution centered 
  on the point that is provided."
  [center]
  (map random/sample (map #(random/distribution :normal {:mu % :sd 50}) center)))


(s/fdef gen-hypersphere
  :args [:josh.meanings.specs/dimensions]
  :ret :josh.meanings.specs/point)
(defn gen-hypersphere
  "Returns a centroid for a hypersphere with dim-count dimensions."
  [dim-count]
  (let [distribution (random/distribution :normal {:mu 0 :sd 1000})]
    (repeatedly dim-count #(random/sample distribution))))


(s/fdef gen-dataset
        :args [:josh.meanings.specs/cluster-count :josh.meanings.specs/dimensions]
        :ret :josh.meanings.specs/points)
(defn gen-dataset
  "Returns a dataset of points sampled from guassian hyperspheres."
  [cluster-count dim-count]
  (shuffle
   (let [hyperspheres (repeatedly cluster-count (partial gen-hypersphere dim-count))]
     (mapcat #(repeatedly 1000 (partial gen-from-hypersphere %)) hyperspheres))))