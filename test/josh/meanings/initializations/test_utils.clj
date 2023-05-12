(ns josh.meanings.initializations.test-utils
  (:require
   [josh.meanings.initializations.utils :as utils]
   [josh.meanings.records.clustering-state :refer [->KMeansState]]
   [clojure.test :refer [deftest is testing]]))



(deftest test-default-chain-length
  (testing "That the chain length is reasonable when set."
    (let [k-means (utils/add-default-chain-length (->KMeansState
                                                   100
                                                   "test.points.csv"
                                                   :csv
                                                   :afk-mc
                                                   :emd
                                                   nil
                                                   identity
                                                   100
                                                   []
                                                   true))]
      (is (< 0 (:m k-means)))
      (is (< (:m k-means) (:size-estimate k-means)))))
  (testing "That when chain lengths are already set the default is not used."
    (let [k-means (utils/add-default-chain-length (->KMeansState
                                                   100
                                                   "test.points.csv"
                                                   "test-centroids.csv"
                                                   :csv
                                                   :afk-mc
                                                   :emd
                                                   1000
                                                   identity
                                                   100
                                                   []
                                                   true))]
      (is (=  (:m k-means) 1000)))))