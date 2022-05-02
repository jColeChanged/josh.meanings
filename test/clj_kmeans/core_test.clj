(ns clj-kmeans.core-test
  (:require [clojure.test :refer :all]
            [clj-kmeans.core :refer :all]))


(defn write-large-test-file
  []
  (with-open [writer (io/writer "test.csv")]
    (->> (repeatedly 10000000 (fn [] (repeatedly 3 #(rand-int 1000))))
         (csv/write-csv writer))))
(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
