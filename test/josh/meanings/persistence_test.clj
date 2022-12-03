(ns josh.meanings.persistence-test
  "Tests the persistence module."
  (:require 
    [clojure.test :refer [deftest testing is]]
    [josh.meanings.persistence :refer [extension filename->format]]))

(deftest test-filename-parsing
  (testing "Test extension parsing gets extension name."
    (is (= "csv" (extension "testing.csv")))
    (is (= "parquet" (extension "testing.parquet")))
    (is (= "arrows" (extension "testing.arrows")))))

(deftest test-keyword-parsing
  (testing "Test parsing filename into format keyword."
    (is (= :csv (filename->format "testing.csv")))
    (is (= :parquet (filename->format "testing.parquet")))
    (is (= :arrows (filename->format "testing.arrows")))))