(ns josh.meanings.distances
  "Multimethod for distance calculations.
   
   The `get-distance-fn` multimethod dispatches based on identity to 
   determine what distance function to use when calculating distances. 
   By default the supported distance function keys are:
   
   - :euclidean
   - :manhattan
   - :chebyshev
   - :correlation
   - :canberra
   - :emd
   - :euclidean-sq
   - :discrete
   - :cosine
   - :angular
   - :jensen-shannon

   The default distance function is :emd which may not be appropriate for all
   use cases since it doesn't minimize the variational distance like euclidean
   would.  If you don't know why :emd is the default you should probably switch 
   to using :euclidean."
  (:require [fastmath.distance :as distance]))


;; I don't want to lock people out of using their preferred distance 
;; functions. So I'm going to implement distance as a multimethod that 
;; way people can choose to provide their own. 
;; 
;; However, this isn't like with initilaization method. We're going to 
;; be calling this as part of our inner loop. In an inner-loop it makes 
;; little to no sense to continually get the distance we want to use. 
;; 
;; So the thing that is a protocol isn't the distance function itself, but 
;; rather the method by which we get the distance function. 
(defmulti get-distance-fn identity)

;; I'm going to start by supporting all the distance functions that fastmath 
;; supports. I'll worry about GPU support and how that might make this more 
;; complicated when I get to that stage. Until then I want to do the simplest 
;; thing which will work.
(defmethod get-distance-fn :euclidean [_] distance/euclidean)
(defmethod get-distance-fn :manhattan [_] distance/manhattan)
(defmethod get-distance-fn :chebyshev [_] distance/chebyshev)
(defmethod get-distance-fn :correlation [_] distance/correlation)
(defmethod get-distance-fn :canberra [_] distance/canberra)
(defmethod get-distance-fn :emd [_] distance/earth-movers)
(defmethod get-distance-fn :euclidean-sq [_] distance/euclidean-sq)
(defmethod get-distance-fn :discrete [_] distance/discrete)
(defmethod get-distance-fn :cosine [_] distance/cosine)
(defmethod get-distance-fn :angular [_] distance/angular)
(defmethod get-distance-fn :jensen-shannon [_] distance/jensen-shannon)

;; To enable generative testing we are keeping track of the keys we make 
;; available. This isn't intended to stop someone from using a different 
;; key. 
(def distance-keys
  [:euclidean
   :manhattan
   :chebyshev
   :correlation
   :canberra
   :emd
   :euclidean-sq
   :discrete
   :cosine
   :angular
   :jensen-shannon])
