(ns josh.meanings.initializations.utils
  (:require [bigml.sampling.reservoir :as res-sample]
            [tech.v3.dataset :as ds]
            [clojure.tools.logging :as log]
            [josh.meanings.persistence :as persist]
            [clojure.spec.alpha :as s]))

(def t-config :josh.meanings.specs/configuration)
(def t-points :josh.meanings.specs/points)


(s/fdef centroids->dataset :args (s/cat :conf t-config :results t-points))
(defn centroids->dataset
  [conf results]
  {:pre [(= (count results) (:k conf))]
   :post [(= (count results) (ds/row-count %))]}
  (ds/->dataset
   (persist/ds-seq->rows->maps
    (persist/read-dataset-seq conf :points)
    results)))


(defn uniform-sample
  [ds-seq n & options]
  (log/info "Getting uniform sample of size" n)
  (let [sample #(apply res-sample/sample (ds/rowvecs %) n options)]
    (apply res-sample/merge (map sample ds-seq))))

(defn weighted-sample
  [ds-seq weight-fn n & options]
  (log/info "Getting weighted sample of size" n)
  (let [sample #(apply res-sample/sample (ds/rowvecs %) n :weigh weight-fn options)]
    (shuffle (apply res-sample/merge (pmap sample ds-seq)))))

(defn shortest-distance-*
  "Denotes the shortest distance from a data point to a 
   center. Which distance to use is decided by the k means 
   configuration."
  [configuration]
  (let [distance-fn (:distance-fn configuration)]
    (fn [point centroids]
      (apply min (map #(distance-fn point %) centroids)))))

(defn shortest-distance-squared-*
  "Denotes the shortest distance from a data point to a 
   center squared. Useful for computing a D^2 sampling 
   distribution."
  [configuration centroids]
  (let [shortest-distance (shortest-distance-* configuration)]
    (fn [point]
      (Math/pow
       (shortest-distance point centroids)
       2))))

(s/fdef add-default-chain-length :args (s/cat :conf t-config :results t-config))
(defn add-default-chain-length
  "For monte carlo methods we need a chain length to use when 
   doing sampling. Although callers can pass in a chain length 
   there are some dangers when doing so - for example if the 
   chain length is low it won't necessarily approximate k means 
   plus plus. Meanwhile if the chian length is too low then 
   there will be no point in doing sampling at all - we could 
   just use k means plus plus rather than approximating it.

   This function checks to see if a chain length is set and if 
   one is then it does nothing, but it nothing is set it uses 
   the formulas provided in the k means plus plus apporximation 
   papers to ddetermine a reasonable chain length."
  [conf]
  ;; TODO: update the configuration to use a new chain length
  ;; if doing so is appropriate
  conf)

