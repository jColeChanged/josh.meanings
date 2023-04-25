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
   to using :euclidean.
   
   For some distance functions GPU support can be enabled by setting the :use-gpu 
   flag to True.  GPU distance support is availalbe for:

    - :emd
   "
  (:refer-clojure
   :exclude
   [get nth assoc get-in merge assoc-in update-in select-keys destructure let fn loop defn defn-])
  (:require [clj-fast.clojure.core :refer [assoc defn fn let]]
            [clojure.core :as c]
            [clojure.core.async :refer [chan]]
            [clojure.spec.alpha :as s]
            [clojure.java.io :as io]
            [fastmath.distance :as fmdistances]
            [ham-fisted.lazy-noncaching :as hfln]
            [josh.meanings.persistence :as p]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.neanderthal :refer [dataset->dense]]
            [uncomplicate.clojurecl
             [core :as cl :refer :all]
             [info :refer :all]]
            [uncomplicate.clojurecl.core :refer :all]
            [uncomplicate.clojurecl.info :refer :all]
            [uncomplicate.commons
             [core :as clojurecl :refer [with-release]]
             [utils :refer [direct-buffer]]]
            [uncomplicate.neanderthal.core :refer [dim entry imin mrows ncols
                                                   rows]]
            [uncomplicate.neanderthal.native :refer [fge fv]]))

(set! *warn-on-reflection* true)

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
(defmethod get-distance-fn :euclidean [_] fmdistances/euclidean)
(defmethod get-distance-fn :manhattan [_] fmdistances/manhattan)
(defmethod get-distance-fn :chebyshev [_] fmdistances/chebyshev)
(defmethod get-distance-fn :correlation [_] fmdistances/correlation)
(defmethod get-distance-fn :canberra [_] fmdistances/canberra)
(defmethod get-distance-fn :emd [_] fmdistances/earth-movers)
(defmethod get-distance-fn :euclidean-sq [_] fmdistances/euclidean-sq)
(defmethod get-distance-fn :discrete [_] fmdistances/discrete)
(defmethod get-distance-fn :cosine [_] fmdistances/cosine)
(defmethod get-distance-fn :angular [_] fmdistances/angular)
(defmethod get-distance-fn :jensen-shannon [_] fmdistances/jensen-shannon)

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


(defn dataset->matrix
  ([conf ds]
   (-> ds
       (ds/select-columns (:col-names conf))
       (dataset->dense :row :float32)))
  ([ds] (dataset->dense ds :row :float32)))


;; GPU based distance functions need to work a little differently.  For CPU 
;; functions we get the function and then we use the distance function during 
;; an inner-loop.  For GPU based distance functions we don't call the distance 
;; function as part of the inner loop, but as part of the outer loop.  The 
;; inner loop is taken care of on the GPUs.
;; 
;; In order to orchestrate the GPU distance calculations we will need a GPU 
;; program and a kernel.  Telling whether GPU support is then as simple as checking 
;; for the presence of the relevant key.
(def gpu-accelerated
  {:emd  {:program "emd_multi.c" :kernel "wasserstein_distances"}})

;; We can tell whether we can use an outerloop GPU program instead of innerloop 
;; function calls by calling a predicate function.  This will allow for seemless 
;; acceleration upgrade when acceleration is available while falling back to the 
;; innerloop function calls when it is not.
(defn is-gpu-accelerated? 
  "Returns true when GPU acceleration is available."
  [conf] 
  (and
   (:use-gpu conf)
   (contains? gpu-accelerated (:distance-key conf))))

;; To the use the GPU we need to setup a context through which we will interact with the GPU.
;; There can be potentially many GPUs and we want to be able to leverage all of them to gain the 
;; maximum possible speed.
(def gpu-context  (atom nil))
(def gpu-contexts (atom nil))

(defn setup-device
  "Sets up an OpenCL device."
  ([distance-configuration length dev]
   (let [ctx (context [dev])
         channel (chan)
         cqueue (command-queue ctx dev)
         program-source (-> distance-configuration :program io/resource slurp)
         program (program-with-source ctx [program-source])
         prog (try
                (build-program! program (str "-DSIZE=" length) channel)
                (catch Exception _
                  (println (build-log program dev))))
         min-program-source (slurp (io/resource "min_index.c"))
         min-program (program-with-source ctx [min-program-source]) 
         min-prog (try
                    (build-program! min-program channel)
                    (catch Exception _
                      (println (build-log min-program dev))))
         sum-program-source (slurp (io/resource "centroids.c"))
         sum-program (program-with-source ctx [sum-program-source])
         sum-prog (try
                    (build-program! sum-program (str "-DSIZE=" length) channel)
                    (catch Exception _
                      (println (build-log sum-program dev))))
         gpu-context-map {:chan channel
                          :dev dev
                          :kernel  (-> distance-configuration :kernel)
                          :ctx ctx
                          :cqueue cqueue
                          :program program
                          :prog prog
                          :min-program min-program
                          :min-prog min-prog
                          :min-kernel "minimum_index"
                          :sum-program sum-program 
                          :sum-prog sum-prog
                          :sum-kernel "sum_by_group"}]
     (reset! gpu-context gpu-context-map)
     gpu-context-map)))


(defn get-device-context
  [configuration matrix]
  (let [device  (->
                 (platforms)
                 (first)
                 (devices)
                 (first))
        distance-configuration (-> configuration
                                   :distance-key
                                   gpu-accelerated)]
    (setup-device distance-configuration (ncols matrix) device)))

(defn setup-devices
  [configuration matrix]
  (let [devices (-> (platforms) (first) (devices))
        distance-configuration (-> configuration :distance-key gpu-accelerated)]
    (reset! gpu-contexts
            (into []
                  (for [device devices]
                    (setup-device distance-configuration (ncols matrix) device))))))


;; During Lloyd iteration it is common to re-use the same centroids while processing 
;; the same distance calculation.  In order to avoid having to write and rewrite the 
;; same centroid buffer once per sequence we split the writing of the centroid buffer 
;; from the writing of the dataset.
(defn write-centroids-buffer!
  [gpu-context ^uncomplicate.neanderthal.internal.host.buffer_block.RealGEMatrix centroids]
  (let [k (mrows centroids)
        centroids-array ^java.nio.FloatBuffer (.buffer centroids)
        cqueue (:cqueue @gpu-context)
        ctx (:ctx @gpu-context)
        cl-centroids (cl-buffer ctx (.capacity centroids-array) :read-only)]
    (enq-write! cqueue cl-centroids centroids-array)
    (swap! gpu-context assoc :cl-centroids cl-centroids)
    (swap! gpu-context assoc :k k)))

;; After processing an entire dataset sequence we need to clear the buffer so that 
;; we don't hold onto more resources than necessary.
(defn release-centroids-buffer!
  [gpu-context]
  (clojurecl/release (:cl-centroids @gpu-context))
  (swap! gpu-context assoc :cl-centroids nil)
  (swap! gpu-context assoc :k nil))


(defmacro with-centroids
  [centroids-ds & forms]
  `(do
     (write-centroids-buffer! gpu-context (dataset->matrix ~centroids-ds))
     (try
       ~@forms
       (finally
         (release-centroids-buffer! gpu-context)))))


(defn teardown-device
  "Tearsdown an OpenCL device."
  ([] (teardown-device @gpu-context))
  ([device-context]
   (doseq [k [:dev :ctx :cqueue :program :prog :min-program :min-prog :sum-program :sum-prog]]
     (clojurecl/release (k device-context)))))


(defmacro with-gpu-context
  [conf & forms]
  `(do
     (get-device-context ~conf
                         (dataset->dense (first (p/read-dataset-seq ~conf :points)) :row :float32))
     (try
       ~@forms
       (finally
         (teardown-device)))))

;; When doing k means clustering there are three types of distances calculations 
;; that typically need to be done.  These are:
;; 
;; 1. Distance Calculation - Single Point
;; 
;; This typically happens during initialization methods that use the distance 
;; metric as a heuristic for approximating good initial clusters.
;; 
;; 2. Multi-Distance Calculation - Multipoint Minimum or Maximum
;; 
;; This type of distance calculation is like the previous distance calculation 
;; except that multiple distances are calculated and the minimum or maximum 
;; distance is returned rather than returning just one distance.  Again, this 
;; shows up during more sophisticated initialization schemes.
;;
;; 3. Multi-Distance Minimum Index
;;
;; This type of distance calculation is like the previous multi-point distance 
;; calculation but instead of returning the actual distance itself the returned 
;; value is the index of the distance.  This is the distance calculation method 
;; used when assigning clusterings during typical k means iterations.


(defn gpu-distance
  "Evaluates many distances in parallel."
  ([device-context
    ^uncomplicate.neanderthal.internal.host.buffer_block.RealGEMatrix matrix]
   (let [num-clusters (:k device-context)
         n (mrows matrix)
         num-distances (* n num-clusters)
         global-size 1024
         num-per (if (mod n global-size) (inc (quot n global-size)) (quot n global-size))
         global-work-size [global-size]
         work-size (work-size global-work-size)
         host-msg ^java.nio.ByteBuffer (direct-buffer (* num-distances Float/BYTES))
         matrix-array ^java.nio.FloatBuffer (.buffer matrix)
         cqueue (:cqueue device-context)
         cl-centroids (:cl-centroids device-context)]
     (with-release [cl-result (cl-buffer (:ctx device-context) (* num-distances Float/BYTES) :write-only)
                    cl-matrix (cl-buffer (:ctx device-context) (.capacity matrix-array) :read-only)
                    cl-kernel (kernel (:prog device-context) (:kernel device-context))]
       (set-args! cl-kernel cl-result cl-matrix cl-centroids (int-array [num-per]) (int-array [n]) (int-array [num-clusters]))
       (enq-write! cqueue cl-matrix matrix-array)
       (enq-kernel! cqueue cl-kernel work-size)
       (enq-read! cqueue cl-result host-msg)
       (finish! cqueue)
       (let [data ^java.nio.FloatBuffer (.asFloatBuffer host-msg)
             res (float-array num-distances)]
         (dotimes [i num-distances]
           (aset res i (.get data)))
         res))))
  ([device-context
    ^uncomplicate.neanderthal.internal.host.buffer_block.RealGEMatrix matrix
    ^uncomplicate.neanderthal.internal.host.buffer_block.RealGEMatrix centroids]
   (let [num-clusters (mrows centroids)
         n (mrows matrix)
         num-distances (* n num-clusters)
         global-size 1024
         num-per (if (mod n global-size) (inc (quot n global-size)) (quot n global-size))
         global-work-size [global-size]
         work-size (work-size global-work-size)
         host-msg ^java.nio.ByteBuffer (direct-buffer (* num-distances Float/BYTES))
         centroids-array ^java.nio.FloatBuffer (.buffer centroids)
         matrix-array ^java.nio.FloatBuffer    (.buffer matrix)
         cqueue (:cqueue device-context)]
     (with-release [cl-result (cl-buffer (:ctx device-context) (* num-distances Float/BYTES) :write-only)
                    cl-matrix (cl-buffer (:ctx device-context) (.capacity matrix-array) :read-only)
                    cl-centroids (cl-buffer (:ctx device-context) (.capacity centroids-array) :read-only)
                    cl-kernel (kernel (:prog device-context) (:kernel device-context))]
       (set-args!
        cl-kernel cl-result cl-matrix cl-centroids (int-array [num-per]) (int-array [n]) (int-array [num-clusters]))
       (enq-write!  cqueue cl-matrix matrix-array)
       (enq-write!  cqueue cl-centroids centroids-array)
       (enq-kernel! cqueue cl-kernel work-size)
       (enq-read!   cqueue cl-result host-msg)
       (finish! cqueue)
       (let [data ^java.nio.FloatBuffer (.asFloatBuffer host-msg)
             res (float-array num-distances)]
         (dotimes [i num-distances]
           (aset res i (.get data)))
         res)))))


(defn gpu-distance-min-index
  "Evaluates many distances in parallel."
  ([device-context
    ^uncomplicate.neanderthal.internal.host.buffer_block.RealGEMatrix matrix]
   (let [num-clusters (:k device-context)
         n (mrows matrix)
         num-distances (* n num-clusters)
         global-size 1024
         num-per (if (mod n global-size) (inc (quot n global-size)) (quot n global-size))
         global-work-size [global-size]
         work-size (work-size global-work-size)
         host-msg ^java.nio.ByteBuffer (direct-buffer (* n Integer/BYTES))
         matrix-array ^java.nio.FloatBuffer (.buffer matrix)
         cqueue (:cqueue device-context)
         cl-centroids (:cl-centroids device-context)]
     (with-release [cl-result (cl-buffer (:ctx device-context) (* num-distances Float/BYTES) :read-write)
                    cl-matrix (cl-buffer (:ctx device-context) (.capacity matrix-array) :read-only)]
       (with-release [cl-kernel (kernel (:prog device-context) (:kernel device-context))]
         (set-args! cl-kernel cl-result cl-matrix cl-centroids (int-array [num-per]) (int-array [n]) (int-array [num-clusters]))
         (enq-write! cqueue cl-matrix matrix-array)
         (enq-kernel! cqueue cl-kernel work-size)
         (finish! cqueue))
       (with-release [cl-min-indexes (cl-buffer (:ctx device-context) (* n Integer/BYTES) :write-only)
                      cl-min-kernel (kernel (:min-prog device-context) (:min-kernel device-context))]
         (set-args! cl-min-kernel cl-result cl-min-indexes (int-array [num-per]) (int-array [n]) (int-array [num-clusters]))
         (enq-kernel! cqueue cl-min-kernel work-size)
         (enq-read! cqueue cl-min-indexes host-msg)
         (finish! cqueue)
         (let [data ^java.nio.IntBuffer (.asIntBuffer host-msg)
               res (int-array n)]
           (dotimes [i n]
             (aset res i (.get data)))
           res)))))
  ([device-context matrix assignments points]
   (let [num-clusters (:k device-context)
         n (mrows matrix)
         num-distances (* n num-clusters)
         global-size 1024
         num-per (if (mod n global-size) (inc (quot n global-size)) (quot n global-size))
         global-work-size [global-size]
         work-size (work-size global-work-size)
         host-msg ^java.nio.ByteBuffer (direct-buffer (* n Integer/BYTES))
         cqueue (:cqueue device-context)
         cl-centroids (:cl-centroids device-context)]
     (with-release [cl-result (cl-buffer (:ctx device-context) (* num-distances Float/BYTES) :read-write)]
       (with-release [cl-kernel (kernel (:prog device-context) (:kernel device-context))]
         (set-args! cl-kernel cl-result points cl-centroids (int-array [num-per]) (int-array [n]) (int-array [num-clusters]))
         (enq-kernel! cqueue cl-kernel work-size)
         (finish! cqueue))
       (with-release [cl-min-kernel (kernel (:min-prog device-context) (:min-kernel device-context))]
         (set-args! cl-min-kernel cl-result assignments (int-array [num-per]) (int-array [n]) (int-array [num-clusters]))
         (enq-kernel! cqueue cl-min-kernel work-size)
         (enq-read! cqueue assignments host-msg)
         (finish! cqueue)
         (let [data ^java.nio.IntBuffer (.asIntBuffer host-msg)
               res (int-array n)]
           (dotimes [i n]
             (aset res i (.get data)))
           res))))))



;; The GPU is so much faster than the CPU that we should be preferring 
;; the way it approaches things to the way that the CPU might break down 
;; the problem.  So our CPU outerloop returns arrays of floats just like 
;; the GPU does.
(defn distances
  "Returns a vector of distance of the centroids from the point."
  [centroids distance-fn point]
  (map (partial distance-fn point) centroids))


(defn cpu-distance
  [configuration dataset centroids]
  (let [distance-fn (get-distance-fn (:distance-key configuration))
        centroids (ds/rowvecs centroids)
        distances-fn (partial distances centroids distance-fn)
        points (ds/rowvecs dataset)]
    (into-array Float/TYPE (mapcat distances-fn points))))


(defn minimum-distance
  [conf ds centroid-ds]
  (let [ds-matrix (dataset->matrix conf ds) 
        row-count (ds/row-count ds)
        centroid-matrix (dataset->matrix conf centroid-ds)
        col-count (ds/row-count centroid-ds) 
        distances (fge row-count col-count (gpu-distance @gpu-context ds-matrix centroid-matrix) {:layout :row})]
    (fv (hfln/map (fn [m] (entry m (imin m))) (rows distances)))))


(defn minimum-index
  [conf ds]
  (let [ds-matrix (dataset->matrix conf ds)]
    (gpu-distance-min-index @gpu-context ds-matrix)))