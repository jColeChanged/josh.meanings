(ns josh.meanings.specs
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [josh.meanings.testbeds.gaussian-hyperspheres :refer [gen-dataset]]
   [tech.v3.dataset :as ds]))


(s/def ::number number?)
(s/def ::point (s/coll-of ::number :min-count 1))
(s/def ::distance (s/and ::number (s/or :pos pos? :zero zero?)))
(s/def ::row 
       (s/and 
        (s/coll-of ::number :min-count 2)
        #(>= (last %) 0)))

(s/def ::dimensions pos-int?)              ;; d
(s/def ::low-d (s/int-in 2 1000))
(s/def ::d (s/with-gen
             (s/or :low-d ::low-d :large-d ::dimensions)
             #(s/gen ::low-d)))

(s/def ::cluster-count pos-int?)           ;; k
(s/def ::low-k (s/int-in 2 1000))
(s/def ::k (s/with-gen
             (s/or :low-k ::low-k :large-k ::cluster-count)
             #(s/gen ::low-k)))

(s/def ::chain-length pos-int?)
(s/def ::low-m (s/int-in 2 10000))
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
          (assoc dataset last-column (map (fn [x] (Math/abs x) (last dataset))))))
      (s/gen ::dataset))))

(s/def ::sampling-datasets
  (s/with-gen
    (s/coll-of ::sampling-dataset)
    #(gen/fmap (juxt identity identity identity identity) (s/gen ::dataset))))

;; (gen/generate (s/gen ::sampling-datasets))



(s/def ::configuration map?)