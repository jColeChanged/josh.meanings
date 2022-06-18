(ns josh.meanings.initializations.test-afk
  (:require [josh.meanings.initializations.afk :as afk]
            [clojure.spec.test.alpha :as stest]))


(stest/check `afk/samples-needed)
(stest/check `afk/qx)
(stest/check `afk/point)
;; (stest/check `afk/samples)

