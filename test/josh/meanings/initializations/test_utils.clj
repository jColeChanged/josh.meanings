(ns josh.meanings.initializations.test-utils
  (:require [josh.meanings.initializations.utils :as utils]
            [tech.v3.dataset :as ds]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

;; When doing markov chain sampling we don't want to ever 
;; run into a situation where our markov chain isn't the 
;; size we expected it to be.

(defn gen-dataset-seq
  [dimensions block-size num-blocks]
  (let [gen-ds-row (gen/vector gen/nat dimensions)
        gen-ds-block (gen/vector gen-ds-row block-size)
        ds-seq-rows (gen/sample gen-ds-block num-blocks)
        row->map (partial zipmap (range dimensions))
        ds-seqs (->> ds-seq-rows
                     (map (partial map row->map))
                     (map ds/->dataset))]
    ds-seqs))

(defn make-positive [x] (inc (Math/abs x)))

(defspec us-size-equal-to-reqed-size-w-rt 100
  (prop/for-all [dims (gen/fmap make-positive gen/small-integer)
                 block-size (gen/fmap make-positive gen/small-integer)
                 blocks (gen/fmap make-positive gen/small-integer)
                 num-samples (gen/fmap make-positive gen/small-integer)]
                (let [ds-seq (gen-dataset-seq dims block-size blocks)]
                  (= (count (utils/uniform-sample ds-seq num-samples :replace true))
                     num-samples))))

(defspec us-size-lte-to-ds-size-wo-r 100
  (prop/for-all [dims (gen/fmap make-positive gen/small-integer)
                 block-size (gen/fmap make-positive gen/small-integer)
                 blocks (gen/fmap make-positive gen/small-integer)
                 num-samples (gen/fmap make-positive gen/small-integer)]
                (let [ds-seq (gen-dataset-seq dims block-size blocks)]
                  (<= (count (utils/uniform-sample ds-seq num-samples :replace false))
                      (* block-size blocks)))))


(defspec ws-size-equal-to-reqed-size-w-rt 100
  (prop/for-all [dims (gen/fmap make-positive gen/small-integer)
                 block-size (gen/fmap make-positive gen/small-integer)
                 blocks (gen/fmap make-positive gen/small-integer)
                 num-samples (gen/fmap make-positive gen/small-integer)]
                (let [ds-seq (gen-dataset-seq dims block-size blocks)]
                  (= (count (utils/weighted-sample ds-seq first num-samples :replace true))
                     num-samples))))

(defspec ws-size-lte-to-ds-size-wo-r 100
  (prop/for-all [dims (gen/fmap make-positive gen/small-integer)
                 block-size (gen/fmap make-positive gen/small-integer)
                 blocks (gen/fmap make-positive gen/small-integer)
                 num-samples (gen/fmap make-positive gen/small-integer)]
                (let [ds-seq (gen-dataset-seq dims block-size blocks)]
                  (<= (count (utils/weighted-sample ds-seq first num-samples :replace false))
                      (* block-size blocks)))))
