(ns josh.meanings.initializations.test-afk
  (:require [clojure.pprint :as pprint]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer [deftest is testing]]
            [josh.meanings.initializations.afk :as afk]))

(defn conforming-to-the-spec?
  ([fn-to-check options]
   (let [results (stest/check [fn-to-check] options)]
     (if (some :failure results)
       (do
         (println "\nFailed specs:")
         (doseq [result results
                 :when (:failure result)]
           (println (:sym result))
           (pprint/pprint (or (ex-data (:failure result))
                              (:failure result)))))
       true)))
  ([fn-to-check]
   (conforming-to-the-spec? fn-to-check {})))

(deftest test-samples-needed
  (testing "That the samples needed function conforms to its spec."
    (is (-> `afk/samples-needed conforming-to-the-spec?))))

(deftest test-qx
  (testing "That the qx function is conforming to its spec"
    (is (-> `afk/qx conforming-to-the-spec?))))

(deftest test-point
  (testing "That the point function is conforming to its spec"
    (is (-> `afk/point conforming-to-the-spec?))))

(deftest test-samples
  (testing "That the samples function is conforming to its spec"
    (is (-> `afk/samples conforming-to-the-spec?))))
