(ns josh.meanings.kmeans-test
  (:require [clojure.test :refer :all]
            [tech.v3.dataset :as ds]
            [josh.meanings.protocols.classifier :refer [load-centroids]]
            [josh.meanings.kmeans :refer [k-means
                                          initialize-k-means-state
                                          initialize-centroids!
                                          calculate-objective
                                          recalculate-means
                                          regenerate-assignments!]]
            [josh.meanings.records.cluster-result]
            [josh.meanings.persistence :refer :all]
            [josh.meanings.distances :as distance])
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
   [1 2 3]
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
  (write-dataset-as-csv small-dataset-filename (small-testing-dataset)))

;; Since we are running multiple tests we also want to be able 
;; to clean up after ourselves. So we need a way to remove the 
;; files that are involved in the k-means calculation between 
;; test runs.
(def small-test-dataset-cleanup-files
  "Files to cleanup betweeen tests."
  (generate-possible-files small-dataset-filename))


(defn very-small-testing-dataset
  "A dataset with easily understood properties such that k means
   correctness is easy to verify."
  []
  [["wins" "losses" "draws"]
   [1 1 0]
   [2 0 0]
   [0 2 0]])


(def very-small-dataset-filename "test.verysmall.csv")

(defn create-very-small-testing-dataset!
  "Creates the small test dataset."
  []
  (write-dataset-as-csv very-small-dataset-filename (very-small-testing-dataset)))

;; Since we are running multiple tests we also want to be able 
;; to clean up after ourselves. So we need a way to remove the 
;; files that are involved in the k-means calculation between 
;; test runs.
(def very-small-test-dataset-cleanup-files
  "Files to cleanup betweeen tests."
  (generate-possible-files very-small-dataset-filename))


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
       (cleanup-files! small-test-dataset-cleanup-files))))

(defmacro with-very-small-dataset
  [& forms]
  `(try
     (cleanup-files! very-small-test-dataset-cleanup-files)
     (create-very-small-testing-dataset!)
     ~@forms
     (finally
       (cleanup-files! very-small-test-dataset-cleanup-files))))


;; (def result 
;;   (with-small-dataset
;;     (let [cluster-result (k-means small-dataset-filename small-k :m 200)]
;;       cluster-result)))

;; (load-centroids result)


(deftest test-equal-inputs-have-equal-assignments
  (with-small-dataset
    (let [cluster-result (k-means small-dataset-filename small-k :m 200)
          assignments ((ds/->dataset "assignments.test.small.parquet" {:key-fn keyword}) :assignments)]
      (testing "That the right number of centroids are returned."
        (is (= small-k (ds/row-count (load-centroids cluster-result)))))
      (testing "That the centroids are unique."
        (is (= (count (set (ds/rowvecs (load-centroids cluster-result)))) small-k)))
      (testing "[1 2 3] are all assigned the same value."
        (is (apply = (take 4 assignments))))
      (testing "[4 5 6] are all assigned the same value."
        (is (apply = (take 6 (drop 4 assignments)))))
      (testing "[7 8 9] are assigned the same value."
        (is (apply = (take 3 (drop 10 assignments)))))))
  (with-very-small-dataset
    (let [cluster-result (k-means very-small-dataset-filename small-k :m 200)]
      (testing "That the right number of centroids are returned."
        (is (= (ds/row-count (load-centroids cluster-result)) small-k)))
      (testing "That the centroids are unique."
        (is (= (count (set (ds/rowvecs (load-centroids cluster-result)))) small-k))))))

(def large-dataset-filename "test.large.csv")
(def large-dataset-k 3)





(def large-test-dataset-cleanup-files
  "Files to cleanup betweeen tests."
  (generate-possible-files large-dataset-filename))


(defn large-testing-dataset
  "A testing dataset with a large number of items, useful for 
   verifying that the program doesn't fail to run when dataset 
   sizes are large."
  []
  (cons ["wins" "losses" "draws"] (repeatedly 100000 (fn [] (repeatedly 3 #(rand-int 1000))))))


(defn create-large-testing-dataset!
  "Creates the large test dataset."
  []
  (write-dataset-as-csv large-dataset-filename (large-testing-dataset)))

;; We would like to be able to setup and destroy the 
;; files between testing runs. Typing out all the setup 
;; code each time would be tedious. So creating a macro 
;; which handles the setup simplifies our work.
(defmacro with-large-dataset
  [& forms]
  `(try
     (cleanup-files! large-test-dataset-cleanup-files)
     (create-large-testing-dataset!)
     ~@forms
     (finally
       (cleanup-files! large-test-dataset-cleanup-files))))


(deftest test-k-means-large-dataset-memory-bound
    (with-large-dataset
      (let [state (initialize-k-means-state large-dataset-filename large-dataset-k {})]
        (testing "Test that initial generation of centroids works on large files."
          (initialize-centroids! state))
        (distance/with-gpu-context state
          (testing "Test that initial generating assignments work on large files."
            (regenerate-assignments! state))
          (testing "Testing that calculating objective works on large files."
            (calculate-objective state))
          (testing "Test that looping generation of centroids works on large files."
            (recalculate-means state))
          (testing "Testing that looping generation of assignments works on large files."
            (regenerate-assignments! state))))))


(deftest testing-cluster-result-configuration-identity
  (with-small-dataset
    (testing "That the configuration keys used in KMeansState matches the keys in the result." 
      (let [config [:m 200 :distance-key :emd :init :afk-mc]
            result (apply k-means small-dataset-filename small-k config)]
        (for [key (keys config)]
          (is (= (key config) (key result))))))))