(ns josh.meanings.kmeans-test
  (:require [clojure.test :refer :all]
            [tech.v3.dataset :as ds]
            [josh.meanings.kmeans :refer [k-means]]
            [josh.meanings.records.cluster-result]
            [josh.meanings.persistence :refer :all])
  (:use [clojure.data.csv :as csv]
        [clojure.java.io :as io]))

;; Given generators for centroids it should be possible to implement an 
;; identity test.check which checks an equivalence relation between finding 
;; the index of a value and classifying a value.


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
(defn write-dataset-as-csv
  [filename dataset]
  (when (not (.exists (clojure.java.io/file filename)))
    (println "Writing dataset to" filename)
    (with-open [writer (io/writer filename)]
      (->> dataset
           (csv/write-csv writer)))))


(def dataset-prefixes
  "A list of dataset prefixes."
  ["centroids." "history." "assignments." ""])

(def datset-suffixes
  "A list of dataset suffixes."
  (map :suffix (vals formats)))

(defn generate-possible-files
  "Generates a list of possible files."
  [original]
  (let [base (clojure.string/replace-first original ".csv" "")]
    (for [prefix dataset-prefixes
          suffix datset-suffixes]
      (str prefix base suffix))))


(def small-dataset-filename "test.small.csv")

(def small-k 3)

(defn small-testing-dataset
  "A dataset with easily understood properties such that k means
   correctness is easy to verify."
  []
  [["wins" "losses" "draws"]
   [0 0 1000]
   [1000 0 0]
   [0 1000 0]
   [0 0 1000]
   [1000 0 0]
   [0 1000 0]
   [0 0 1000]
   [1000 0 0]
   [0 1000 0]
   [0 0 1000]
   [1000 0 0]
   [0 1000 0]
   [0 0 1000]
   [1000 0 0]
   [0 1000 0]
   [500 0 500]
   [500 500 0]
   [0 500 500]
   [500 0 500]
   [500 500 0]
   [0 500 500]
   [500 0 500]
   [500 500 0]
   [0 500 500]
   [500 0 500]
   [500 500 0]
   [0 500 500]])


(defn create-small-testing-dataset!
  "Creates the small test dataset."
  []
  (write-dataset-as-csv small-dataset-filename (small-testing-dataset)))

;; Since we are running multiple tests we also want to be able 
;; to clean up after ourselves. So we need a way to remove the 
;; files that are involved in the k-means calculation between 
;; test runs.
(def small-test-dataset-cleanup-files
  "Files to cleanup betweeen tests."
  (generate-possible-files small-dataset-filename))


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
     (cleanup-files! small-test-dataset-cleanup-files)
     (create-small-testing-dataset!)
     ~@forms
     (finally
       (cleanup-files! small-test-dataset-cleanup-files))))()

(deftest test-equal-inputs-have-equal-assignments
  (with-small-dataset
    (let [cluster-result (k-means small-dataset-filename small-k :m 200)]
      (testing "That the centroids are unique."
        (is (= (count (set (ds/rowvecs (:centroids cluster-result)))) small-k))))))


(deftest testing-cluster-result-configuration-identity
  (with-small-dataset
    (testing "That the configuration keys used in KMeansState matches the keys in the result." 
      (let [config [:m 200 :distance-key :emd :init :afk-mc]
            result (apply k-means small-dataset-filename small-k config)]
        (for [key (keys config)]
          (is (= (key config) (key result))))))))
