(ns josh.meanings.kmeans-test
  (:require [clojure.test :refer :all]
            [tech.v3.dataset :as ds]
            [josh.meanings.kmeans :refer :all])
  (:use [clojure.data.csv :as csv]
        [clojure.java.io :as io]))


;; I'm hoping to accomplish two things with these tests.
;; 
;; The first, obviously, is to establish correctness. My k-means 
;; implementation should find the k-means it ought to find when 
;; the appropriate clusters exist.
;;
;; The second, less obvious, but just as critical, is that the 
;; k-means computation can be carried out even under non-ideal 
;; circumstances such as when there is little memory and a very 
;; large dataset.
;;
;; Correctness is the most important thing so we'll start with 
;; verifying correctness. 
;;
;; In order to run the algorithm we need to have datasets available.
;; In particular we need to be able to write a small dataset.
(defn write-dataset
  [filename dataset]
  (when (not (.exists (clojure.java.io/file filename)))
    (println "Writing dataset to" filename)
    (with-open [writer (io/writer filename)]
      (->> dataset
           (csv/write-csv writer)))))

(def small-dataset-filename "test.small.csv")

(def small-k 3)

(defn small-testing-dataset
  "A dataset with easily understood properties such that k means
   correctness is easy to verify."
  []
  [[1 2 3]
   [1 2 3]
   [1 2 3]
   [1 2 3]
   [4 5 6]
   [4 5 6]
   [4 5 6]
   [4 5 6]
   [4 5 6]
   [4 5 6]
   [7 8 9]
   [7 8 9]
   [7 8 9]])

(defn create-small-testing-dataset!
  "Creates the small test dataset."
  []
  (write-dataset small-dataset-filename (small-testing-dataset)))

;; Since we are running multiple tests we also want to be able 
;; to clean up after ourselves. So we need a way to remove the 
;; files that are involved in the k-means calculation between 
;; test runs.
(def small-test-dataset-cleanup-files
  "Files to cleanup betweeen tests."
  ["test.small.csv"
   "test.small.arrows"
   "centroids.test.small.csv"
   "assignments.test.small.arrows"
   "history.test.small.csv"])

(defn cleanup-files!
  "Removes a collection of files from the disk."
  [files]
  (doall (map #(io/delete-file % true) files)))

;; We would like to be able to setup and destroy the 
;; files between testing runs. Typing out all the setup 
;; code each time would be tedious. So creating a macro 
;; which handles the setup simplifies our work.
(defmacro with-small-dataset
  [& forms]
  `(try
     (create-small-testing-dataset!)
     ~@forms
     (finally
       (cleanup-files! small-test-dataset-cleanup-files))))


(deftest test-equal-inputs-have-equal-assignments
  (with-small-dataset
    (testing "That the program runs without throwing an exception."
      (is (= nil (k-means small-dataset-filename 3)))
      (let [assignments ((ds/->dataset "assignments.test.small.arrows") "assignments")]
        (testing "[1 2 3] are all assigned the same value."
          (is (apply = (take 4 assignments))))
        (testing "[4 5 6] are all assigned the same value."
          (is (apply = (take 6 (drop 4 assignments)))))
        (testing "[7 8 9] are assigned the same value."
          (is (apply = (take 3 (drop 10 assignments)))))))))



;; (def large-dataset-filename "test.large.csv")
;; (defn large-testing-dataset
;;   "A testing dataset with a large number of items, useful for 
;;    verifying that the program doesn't fail to run when dataset 
;;    sizes are large."
;;   (repeatedly 10000000 (fn [] (repeatedly 3 #(rand-int 1000)))))





;; (deftest test-get-domain
;;   (let [filename "test.csv"]
;;     (write-dataset filename (large-testing-dataset))
;;     (testing "Test that getting domain on a large file works."
;;       (println (find-domain filename)))))


;; (deftest test-generate-assignments
;;   (let [filename "test.csv"]
;;     (write-large-test-file filename 100000000)
;;     (let [state (initialize-k-means-state "test.csv" 3)]
;;       (testing "Test that generating assignments work on large files."
;;         (generate-assignments state)))))
