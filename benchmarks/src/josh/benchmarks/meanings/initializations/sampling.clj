(ns josh.benchmarks.meanings.initializations.sampling
  "Quick benches to optimize speed of sampling during the initialization step."
  (:require
   [clj-async-profiler.core :as prof]
   [criterium.core :refer [quick-bench]]
   [josh.meanings.distances :refer [get-distance-fn]]
   [josh.meanings.initializations.afk :refer [qx-column-name]]
   [josh.meanings.initializations.utils :refer
    [weighted-sample weighted-sample-s]]
   [tech.v3.dataset :as ds]))



;; The resulting flamegraph will be stored in /tmp/clj-async-profiler/results/
;; You can view the HTML file directly from there or start a local web UI:

;; (prof/serve-ui 8080) ; Serve on port 8080

(def test-configuration
  {:k 500
   :size-estimate 2809475760
   :col-names ["wins", "losses", "draws"]
   :distance-key :emd
   :distance-fn (get-distance-fn :emd)
   :init :afk-mc
   :m 97530
   :num-seqs 29265
   :points "/home/joshua/Projects/x.parquet"})


;; (do 
;;   (quick-bench (sample-one test-configuration))
;;   (quick-bench (sample-one-2 test-configuration))
;;   (quick-bench (sample-one-3 test-configuration)))

(defn generate-test-ds
  []
  (ds/->dataset (repeatedly 50000 (fn []
                                    {:wins   (rand-int 900)
                                     :losses (rand-int 900)
                                     :draws  (rand-int 900)
                                     qx-column-name  (inc (rand))}))))


  ;; (let [test-ds-seq (doall (repeat 100 (generate-test-ds)))]

  ;;   (println "uniform-sample-1")
  ;;   (quick-bench (uniform-sample test-ds-seq 1))

  ;;   (println "uniform-sample-2")
  ;;   (quick-bench (uniform-sample-2 test-ds-seq 1))

  ;;   (println "uniform-sample-2")
  ;;   (quick-bench (uniform-sample-3 test-ds-seq 1))))


(defn benchmark-sampling-fns
  []
  (let [test-ds-seq (doall (repeat 100 (generate-test-ds)))]
    (println "weighted-sample-1")
    (quick-bench (weighted-sample   test-ds-seq qx-column-name 60000))
    (println "weighted-sample-2")
    (quick-bench (weighted-sample-s test-ds-seq qx-column-name 60000))))


;; (benchmark-sampling-fns)

;; (let [test-ds-seq (doall (repeat 100 (generate-test-ds)))]
;;   (prof/profile
;;    (dotimes [i 1000]
;;      (weighted-sample   test-ds-seq qx-column-name 60000))))

;;(prof/serve-ui 8080)

;; (defn arg-min-n
;;   "Return the indexes of the top minimum items.  Values must be countable and random access.
;;   Same options,arguments as [[argsort]]."
;;   ([N comparator {:keys [nan-strategy]
;;                   :or {nan-strategy :last}}
;;     values]
;;    (let [N (long N)
;;          val-dtype (dtype-base/operational-elemwise-datatype values)
;;          comparator (-> (find-base-comparator comparator val-dtype)
;;                         (index-comparator nan-strategy values))
;;          queue (PriorityQueue. (int N) (.reversed comparator))
;;          n-elems (dtype-base/ecount values)]
;;      (if (instance? IntComparator comparator)
;;        (dotimes [idx n-elems]
;;          (.offer queue (unchecked-int idx))
;;          (when (> (.size queue) N)
;;            (.poll queue)))
;;        (dotimes [idx n-elems]
;;          (.add queue idx)
;;          (when (> (.size queue) N)
;;            (.poll queue))))
;;      (reduce (hamf-rf/indexed-accum
;;               acc idx v
;;               (ArrayHelpers/aset ^ints acc idx (unchecked-int v))
;;               acc)
;;              (int-array (.size queue))
;;              queue)))
;;   ([N comparator values] (arg-min-n N comparator nil values))
;;   ([N values] (arg-min-n N nil nil values)))
