(ns josh.meanings.initializations.afk-test 
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [josh.meanings.initializations.afk :refer [qx-file]]))

(deftest test-qx-file
  (testing "That the path returned is in the same directory as the original."
    (is (= (qx-file {:points "/media/joshua/a/database/user_files/river.arrow"})
           "/media/joshua/a/database/user_files/qx.arrow"))))