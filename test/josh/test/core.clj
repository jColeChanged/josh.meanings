(ns josh.test.core
  (:require
   [clojure.test :as t]
   [clojure.string :as str]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]))

;; https://stackoverflow.com/questions/40697841/howto-include-clojure-specd-functions-in-a-test-suite/40711634#40711634
(alias 'stc 'clojure.spec.test.check)

;; extracted from clojure.spec.test.alpha
(defn failure-type [x] (::s/failure (ex-data x)))
(defn unwrap-failure [x] (if (failure-type x) (ex-data x) x))

;; modified from clojure.spec.test.alpha
(defn abbrev-result [x]
  (if (-> x :stc/ret :pass?)
    (dissoc x :spec ::stc/ret)
    (-> (dissoc x ::stc/ret)
        (update :spec s/describe)
        (update :failure unwrap-failure))))

(defn throwable? [x]
  (instance? Throwable x))

(defn failure-report [failure]
  (let [abbrev (abbrev-result failure)
        expected (->> abbrev :spec rest (apply hash-map) :ret)
        reason (:failure abbrev)]
    (if (throwable? reason)
      {:type :error
       :message "Exception thrown in check"
       :expected expected
       :actual reason}
      (let [data (ex-data (get-in failure
                                  [::stc/ret
                                   :shrunk
                                   :result-data
                                   :clojure.test.check.properties/error]))]
        {:type     :fail
         :message  (with-out-str (s/explain-out data))
         :expected expected
         :actual   (::s/value data)}))))

(defn check?
  [msg [_ body :as form]]
  `(let [results# ~body
         failures# (remove (comp :pass? ::stc/ret) results#)]
     (if (empty? failures#)
       [{:type    :pass
         :message (str "Generative tests pass for "
                       (str/join ", " (map :sym results#)))}]
       (map failure-report failures#))))

(defmethod t/assert-expr 'check?
  [msg form]
  `(dorun (map t/do-report ~(check? msg form))))