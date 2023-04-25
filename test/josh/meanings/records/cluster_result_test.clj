(ns josh.meanings.records.cluster-result-test
  "Unit tests for the ClusterResult record."
  (:require
   [tech.v3.dataset :as ds]
   [josh.meanings.protocols.savable :refer [save-model  Savable]]
   [josh.meanings.protocols.classifier :refer [load-assignments load-centroids]]
   [clojure.test :refer [deftest testing is]]
   [josh.meanings.records.cluster-result :refer [load-model map->ClusterResult]]))



(def cluster-result-map 
  {:centroids "test/josh/meanings/records/cluster_result_centroids.csv"
   :assignments "test/josh/meanings/records/cluster_result_assignments.csv"
   :cost 0.0
   :format :csv
   :configuration {:m 10
                   :k 2
                   :col-names ["col1" "col2" "col3"]
                   :distance-key :euclidean
                   :init :random}})
(def test-cluster (map->ClusterResult cluster-result-map))

(deftest test-savable-protocol
  (testing "That the ClusterResult record implements the Savable protocol."
    (is (satisfies? Savable test-cluster)))
  (testing "That the ClusterResult record can be saved."
    (let [filename "test/josh/meanings/records/cluster_result_test.edn"]
      (is (= filename (save-model test-cluster filename)))
      (testing "That after the ClusterResult record has been saved it can be loaded."
        (let [loaded-cluster-result (load-model filename)]
          (testing "That once loaded it equal to the original."
            (is (= test-cluster loaded-cluster-result))))))))


(deftest test-loading-centroids
  (testing "That the centroid dataset can be loaded from disk."
    (let [centroids-dataset (load-centroids test-cluster)]
      (testing "That the centroid dataset is a dataset."
        (is (ds/dataset? centroids-dataset)))
      (testing "That the centroid dataset has the correct number of columns."
        (is (= 3 (ds/column-count centroids-dataset))))
      (testing "That the column names match the configuration."
        (is (= (-> test-cluster :configuration :col-names) (ds/column-names centroids-dataset))))
      (testing "That the row count matches with k."
        (is (= (-> test-cluster :configuration :k) (ds/row-count centroids-dataset)))))))



(deftest test-assignment-loading
  (testing "That the assignments dataset can be loaded from disk."
    (let [assignments-dataset (load-assignments test-cluster)]
      (testing "That the assignments dataset is a sequence of datasets."
        (is (seq? assignments-dataset))
        (is (ds/dataset? (first assignments-dataset))))
      (testing "That the assignments dataset has a 'assignments' column."
        (let [first-assignment (first assignments-dataset)]
          (is (some #{"assignments"} (ds/column-names first-assignment)))))))) 




