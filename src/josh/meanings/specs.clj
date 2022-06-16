(ns josh.meanings.specs
  (:require
   [clojure.spec.alpha :as s]
   [tech.v3.dataset :as ds]))

(s/def ::number number?)
(s/def ::point (s/coll-of ::number :min-count 1))
(s/def ::distance (s/and ::number (s/or :pos pos? :zero zero?)))
(s/def ::row 
       (s/and 
        (s/coll-of ::number :min-count 2)
        #(>= (last %) 0)))

(s/def ::dimensions pos-int?)              ;; d
(s/def ::cluster-count pos-int?)           ;; k
(s/def ::chain-length pos-int?)            ;; m
(s/def ::sample-count integer?)

(s/def ::points (s/coll-of ::point))
(s/def ::rows (s/coll-of ::row))
(s/def ::dataset ds/dataset?)
(s/def ::dataset-seq (s/coll-of ::dataset))
(s/def ::configuration map?)