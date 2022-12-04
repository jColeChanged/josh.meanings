(ns josh.meanings.specs
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [josh.meanings.testbeds.gaussian-hyperspheres :refer [gen-dataset]]
   [josh.meanings.distances :refer [distance-keys]]
   [josh.meanings.persistence :refer [formats]]
   [josh.meanings.initializations.core :refer [initialization-keys]]
   [josh.file.utils :as futils]
   [tech.v3.dataset :as ds]
   [tech.v3.datatype.functional :as dfn]))


(s/def ::number number?)
(s/def ::point (s/coll-of ::number :min-count 1))
(s/def ::points (s/coll-of :point :min-count 1))

(s/def ::distance (s/and ::number (s/or :pos pos? :zero zero?)))
(s/def ::row
  (s/and
   (s/coll-of ::number :min-count 2)
   #(>= (last %) 0)))

(s/def ::dimensions pos-int?)              ;; d
(s/def ::low-d (s/int-in 2 100))
(s/def ::d (s/with-gen
             (s/or :low-d ::low-d :large-d ::dimensions)
             #(s/gen ::low-d)))

(s/def ::cluster-count pos-int?)           ;; k
(s/def ::low-k (s/int-in 2 50))
(s/def ::k (s/with-gen
             (s/or :low-k ::low-k :large-k ::cluster-count)
             #(s/gen ::low-k)))

(s/def ::chain-length pos-int?)
(s/def ::low-m (s/int-in 2 100))
(s/def ::m (s/with-gen
             (s/or :low-m ::low-m ::high-m ::chain-length)
             #(s/gen ::low-m)))

(s/def ::sample-count integer?)

(s/def ::points (s/coll-of ::point))
(s/def ::rows (s/coll-of ::row))
(s/def ::dataset (s/with-gen
                   ds/dataset?
                   #(gen/fmap
                     (partial apply gen-dataset)
                     (s/gen (s/cat :k ::k :d ::d)))))


(s/def ::col-name string?)
(s/def ::col-names (s/coll-of ::col-name :distinct true))

(s/def ::dataset-seq
  (s/with-gen
    (s/coll-of ::dataset)
    #(gen/fmap
      (juxt identity identity identity identity)
      (s/gen ::dataset))))

(s/def ::sampling-dataset
  (s/with-gen
    ds/dataset?
    #(gen/fmap
      (fn [dataset]
        (let [last-column (last (ds/column-names dataset))]
          (assoc dataset last-column (dfn/abs (dataset last-column)))))
      (s/gen ::dataset))))

(s/def ::sampling-datasets
  (s/with-gen
    (s/coll-of ::sampling-dataset)
    #(gen/fmap (juxt identity identity identity identity) (s/gen ::sampling-dataset))))

(s/def ::distance-key
  (s/with-gen keyword? #(s/gen distance-keys)))

(s/def ::init-key
  (s/with-gen keyword? #(s/gen initialization-keys)))

(s/def ::format-key (set (keys formats)))




(s/def ::configuration
  (s/keys :req-un
          [:josh.meanings.specs/k
           string?
           string?
           string?
           :josh.meanings.specs/format-key
           :josh.meanings.specs/init-key
           :josh.meanings.specs/distance-key
           ifn?
           :josh.meanings.specs/m
           ifn?]))



(s/def ::filename (s/and string? futils/file?))

(s/def ::cost number?)

(s/def ::cluster-result-configuration
  (s/keys :req-un
          [:josh.meanings.specs/k
           :josh.meanings.specs/m
           :josh.meanings.specs/distance-key
           :josh.meanings.specs/init]))

(s/def ::cluster-result
  (s/keys :req-un 
          [:josh.meanings/filename
           :josh.meanings/filename
           :josh.meanings/format-key
           :josh.meanings/cost
           :josh.meanings/cluster-result-configuration]))