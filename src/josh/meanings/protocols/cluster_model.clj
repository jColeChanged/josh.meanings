(ns josh.meanings.protocols.cluster-model)


(defprotocol PClusterModel
  (save-model [this filename])
  (load-assignments [this])
  (classify [this x]))
