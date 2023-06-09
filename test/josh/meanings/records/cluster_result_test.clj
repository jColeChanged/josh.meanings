(ns josh.meanings.records.cluster-result-test
  "Unit tests for the ClusterResult record."
  (:require
   [josh.meanings.protocols.savable :refer [save-model]]
   [josh.meanings.protocols.classifier :refer [assignments]]
   [clojure.test :refer [deftest testing is]]
   [josh.meanings.records.clustering-state :refer [map->KMeansState]]
   [josh.meanings.records.cluster-result :refer [load-model map->ClusterResult]]
   [josh.meanings.kmeans]
   [tech.v3.dataset :as ds]
   [josh.meanings.distances :as distances]))


(defn test-dataset  
  [] 
  (let [test-centroids-data {"col1" [1, 2, 3]
                             "col2" [3, 4, 5]
                             "col3" [6, 7, 8]}]
    (ds/->dataset test-centroids-data)))


(deftest test-save-and-load
  (let [test-centroid-dataset (test-dataset)
        test-cluster-result-map {:centroids test-centroid-dataset
                                 :cost 0.0
                                 :configuration {:m 10
                                                 :k 2
                                                 :format :arrow 
                                                 :col-names ["col1" "col2" "col3"]
                                                 :distance-key :euclidean
                                                 :init :random}}
        test-cluster (map->ClusterResult test-cluster-result-map)]
    (testing "That the ClusterResult record can be saved."
      (let [filename "test/josh/meanings/records/cluster_result_test.edn"]
        (save-model test-cluster filename)
        (let [loaded-results (load-model filename)]
          (testing "That after the ClusterResult record has been saved it can be loaded."
            (is (= test-cluster loaded-results))))))))


(deftest test-assignments
  (let [test-centroids-data {"col1" [1, 2, 3]
                             "col2" [3, 4, 5]
                             "col3" [6, 7, 8]}
        test-centroid-dataset (ds/->dataset test-centroids-data)
        test-cluster-result-map {:centroids test-centroid-dataset
                                 :cost 0.0
                                 :configuration (map->KMeansState
                                                 {:m 10
                                                  :k 2
                                                  :col-names ["col1" "col2" "col3"]
                                                  :format :arrow
                                                  :distance-key :emd
                                                  :init :random
                                                  :use-gpu true})}
        test-datasets (map ds/->dataset
                           (repeat 10 {"col1" [1, 2, 3]
                                       "col2" [3, 4, 5]
                                       "col3" [6, 7, 8]}))
        test-cluster-result (map->ClusterResult test-cluster-result-map)]
    (testing "That assignments go 0, 1, 2"
      (let [result-dataset (ds/->dataset {"col1" [1, 2, 3]
                                          "col2" [3, 4, 5]
                                          "col3" [6, 7, 8]
                                          :assignments [0, 1, 2]})]
        (let [configuration (-> test-cluster-result
                                :configuration
                                (assoc :centroids (:centroids test-cluster-result)))]
          (distances/with-gpu-context configuration
            (distances/with-centroids (:centroids test-cluster-result)
              (let [assignment-results (assignments test-cluster-result test-datasets)]
                (is (= (repeat 10 result-dataset) assignment-results))))))))))
