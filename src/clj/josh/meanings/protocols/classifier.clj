(ns josh.meanings.protocols.classifier)

(defprotocol Classifier
  (assignments [this datasets]))