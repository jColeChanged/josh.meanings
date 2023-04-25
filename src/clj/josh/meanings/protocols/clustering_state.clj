(ns josh.meanings.protocols.clustering-state)


(defprotocol PClusteringState
  
  (configuration [this])
  (column-names [this])
  (load-centroids [this])
  (load-points [this])
  (load-assignments [this]))