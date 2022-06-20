(ns josh.meanings.initializations.core)

(defmulti initialize-centroids
  "Initializes centroids according to the initialization 
   method specified in the configuration."
  :init)


;; We want to enable generative testing. To do that we 
;; need to have a list of supported keys somewhere. We're 
;; going to put that here. This doesn't mean that this list 
;; is all the initialization methods we support. For example, 
;; anyone using the library is free to build their own 
;; initialization method.
(def initialization-keys
  [:afk-mc
   :k-mc-squared
   :niave
   :k-means-parallel
   :k-means-++])
