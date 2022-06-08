(ns josh.meanings.initializations.utils
  (:require [bigml.sampling.reservoir :as res-sample]
            [tech.v3.dataset :as ds]
            [clojure.tools.logging :as log]))

(defn uniform-sample
  [ds-seq n]
  (log/info "Getting uniform sample of size" n)
  (apply res-sample/merge
         (map #(res-sample/sample (ds/rowvecs %) n) ds-seq)))

(defn weighted-sample
  [ds-seq weight-fn n]
  (log/info "Getting weighted sample of size" n)
  (apply res-sample/merge
         (map #(res-sample/sample (ds/rowvecs %) n :weigh weight-fn) ds-seq)))

