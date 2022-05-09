(ns josh.meanings.core-test
  (:require [clojure.test :refer :all]
            [clj-kmeans.core :refer :all])
  (:use [clojure.data.csv :as csv]
        [clojure.java.io :as io]))


(defn write-large-test-file
  [filename num-rows]
  (when (not (.exists (clojure.java.io/file filename)))
    (println "Writing extremely large test file...")
    (with-open [writer (io/writer filename)]
      (->> (repeatedly num-rows (fn [] (repeatedly 3 #(rand-int 1000))))
           (csv/write-csv writer)))
    (println "Done writing extremely large test file.")))



(deftest test-get-domain
  (let [filename "test.csv"]
    (write-large-test-file filename 1000000000000)
    (testing "Test that getting domain on a large file works."
      (println (find-domain filename)))))


(deftest test-generate-assignments
  (let [filename "test.csv"]
    (write-large-test-file filename 100000000)
    (let [state (initialize-k-means-state "test.csv" 3)]
      (testing "Test that generating assignments work on large files."
        (generate-assignments state)))))
