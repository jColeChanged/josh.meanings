(ns josh.meanings.persistence-test
  "Tests the persistence module."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [josh.meanings.persistence :refer
             [extension filename->format formats read-dataset-seq]]
            [tech.v3.dataset :as ds]))

;; Common data
;; -----------

(def test-formats
  [:parquet :arrow :arrows :csv])

(def test-dataset-seq
  [(ds/->dataset [{"a" 1 "b" 2 "c" 3} {"a" 4 "b" 5 "c" 6} {"a" 7 "b" 8 "c" 9}])])

(def test-dataset-filepath-prefix
  "test-dataset")

;; Cleanup after tests run
;; ------------------------

(defn delete-test-files []
  (doseq [format test-formats]
    (let [suffix (-> formats format :suffix)
          filename (str test-dataset-filepath-prefix suffix)
          file (clojure.java.io/file filename)]
      (when (.exists file)
        (clojure.java.io/delete-file filename)))))

(defn file-cleanup-fixture [test-fn]
  (test-fn)
  (delete-test-files))

(use-fixtures :once file-cleanup-fixture)


;; Tests
;; -----

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

(defn write-test-dataset [format]
  (let [writer-fn (-> formats format :writer)
        suffix (-> formats format :suffix)
        filename (str test-dataset-filepath-prefix suffix)]
    (writer-fn filename test-dataset-seq)
    filename))


(deftest test-read-dataset-seq-columns
  (doseq [format test-formats]
    (let [filename (write-test-dataset format)
          state {:points filename
                 :col-names ["a" "c"]}
          result-dataset (first (read-dataset-seq state :points))
          test-dataset (first test-dataset-seq)]
      (is (= (ds/column-names result-dataset) ["a" "c"]))
      (is (= (ds/row-count result-dataset) (ds/row-count test-dataset)))
      (is (= result-dataset
             (ds/select-columns test-dataset ["a" "c"]))))))

