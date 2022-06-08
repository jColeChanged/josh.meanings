(ns josh.meanings.initializations.core)

(defmulti initialize-centroids
  "Initializes centroids according to the initialization 
   method specified in the configuration."
  :init)