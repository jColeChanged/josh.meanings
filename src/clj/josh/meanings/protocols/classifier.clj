(ns josh.meanings.protocols.classifier)


(defprotocol Classifier
  (load-assignments [this])
  (load-centroids [this])
  (classify [this x]))