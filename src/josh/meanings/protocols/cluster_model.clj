(ns josh.meanings.protocols.cluster-model
  "The `josh.meanings.protocols.cluster-model` namespace defines a protocol for
   manipulating cluster models that have already been trained. 

   The `PClusterModel` protocol defines the interface for cluster model 
   implementations, including methods for saving and loading the model data, and 
   classifying points using the model.

   Any implementation of the `PClusterModel` protocol must provide concrete 
   implementations of the protocol methods, and adhere to the input and output 
   specs defined in the protocol.")


(defprotocol PClusterModel
  (save-model [this filename])
  (load-assignments [this])
  (load-centroids [this])
  (classify [this x]))
