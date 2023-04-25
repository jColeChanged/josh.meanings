(ns josh.benchmarks.meanings.initializations.qx
  "Quick benches to optimize speed of sampling during the initialization step."
  (:require [josh.meanings.distances :as distance :refer
             [get-distance-fn]]
            [josh.meanings.kmeans :refer [lloyd]]
            [josh.meanings.records.clustering-state :refer [map->KMeansState]]
            [tech.v3.dataset :as ds]
            [uncomplicate.neanderthal.core :as ne :refer :all]
            [uncomplicate.neanderthal.native :refer :all]
            [uncomplicate.neanderthal.vect-math :as vm :refer :all]))


(def test-configuration-2
  {:k 500
   :size-estimate 2809475760
   :col-names ["wins", "losses", "draws"]
   :distance-key :emd
   :format-key :arrow
   :distance-fn (get-distance-fn :emd)
   :init-key :afk-mc
   :m 97530
   :use-gpu true
   :num-seqs 29265
   :centroids "/media/joshua/a/centroids.arrow"
   :assignments "/media/joshua/a/assignments.arrow"
   :points "/media/joshua/a/un_x.arrow"})


(def configuration (map->KMeansState test-configuration-2))


(ds/write!
 (distance/with-gpu-context configuration
   (lloyd configuration))
 "centroids2.arrow")
  




