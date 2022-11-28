(ns josh.meanings.initializations.mc2
  "Approximating K-Means in sublinear time is a paper written by 
   Olivier Bachmem, Mario Lucic, Hamed Hassani, and Andreas Krause 
   which shares a method for obtaining a provably good approximation
   of k-means++ in sublinear time. The method they share uses markov 
   chain monte carlo sampling in order to approximate the D^2 sampling 
   that is used in k-means++. Since this method is proven to converge to 
   drawing from the same distribution as D^2 sampling in k-means++ the 
   theoretical competitiveness guarantees of k-means++ are inherited. 
   This algorithm is sublinear with respect to input size which makes 
   it different from other variants of k-means++ like k-means||. Whereas
   a variant like k-means|| allows for a distributed k-means++ computation 
   to be carried out across a cluster of computers, k-means-mc++ is 
   better suited to running on a single machine."
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as p]
   [josh.meanings.initializations.utils :refer [centroids->dataset uniform-sample add-default-chain-length]]
   [clojure.spec.alpha :as s]
   [clojure.test :refer [is]])
  (:use
   [josh.meanings.initializations.core]))


(def t-dataset :josh.meanings.specs/dataset)
(def t-datasets :josh.meanings.specs/datasets)
(def t-cluster-count :josh.meanings.specs/k)
(def t-chain-length :josh.meanings.specs/m)
(def t-point :josh.meanings.specs/point)
(def t-points :josh.meanings.specs/points)
(def t-config :josh.meanings.specs/configuration)

;; In the paper they formulate sampling such that sampling is carried out 
;; one uniform sample at a time. I'm not going to do that. Instead I'm going 
;; to get one large sample as my first step. Doing this means we won't be 
;; doing both the CPU intensive and disk intensive parts of our algorithm 
;; at the same time.
(s/fdef samples
  :args (s/cat :ds-seq t-datasets :k t-cluster-count :m t-chain-length)
  :ret t-points)
(defn- samples
  "Get all the samples we'll need for the markov chain."
  [ds-seq k m]
  ;; 1 initial cluster, (k - 1) remaining clusters, each of which need 
  ;; to generate a markov chain of length m
  (uniform-sample ds-seq (+ 1 (* (- k 1) m)) :replace true))

(defn- square
  [x]
  (* x x))

(defn make-weight-fn
  "Create a function which computes the weight of a point given the 
   current set of clusters."
  [distance-fn clusters]
  (fn [p2]
    (square (apply min (for [p1 clusters] (distance-fn p1 p2))))))


(s/fdef mcmc-sample :args (s/cat :distance-fn ifn? :c t-point :rsp t-points) :ret t-points)
(defn- mcmc-sample
  "Perform markov chain monte carlo sampling to approxiate D^2 sampling"
  [weight-fn c rsp]

  ;; not special casing first selection because it decomplicates the 
  ;; inner loop code so that we're doing the same thing each time 
  ;; without requiring a wrapping let
  (loop [ps rsp
         dseq (map weight-fn rsp)
         rands (repeatedly (count rsp) rand)
         x (first ps)
         dx (first dseq)]
    (if (empty? ps)
      x
      (let [take (or (zero? dx) (> (/ (first dseq) dx) (first rands)))]
        (recur
         (rest ps)
         (rest dseq)
         (rest rands)
         (if take (first ps)    x)
         (if take (first dseq) dx))))))




(s/fdef k-means-mc-2-initialization :args (s/cat :conf t-config) :ret t-dataset)
(defn- k-means-mc-2-initialization
  [conf]
  (log/info "Performing k-mc^2 initialization")
  (centroids->dataset
   conf
   (let [k (:k conf)   ;; number of clusters
         m (:m conf)   ;; markov chain length
         sp (samples (p/read-dataset-seq conf :points) k m)]
     (loop [c (first sp) cs [c] rsp (rest sp)]
       (let [weight-fn (make-weight-fn (:distance-fn conf) cs)]
         (log/info "Performing round of mcmc sampling")
         (if (empty? rsp)
           cs
           (let [nc (mcmc-sample weight-fn c (take m rsp))]
             (recur nc (conj cs nc) (drop m rsp)))))))))

(defmethod initialize-centroids
  :k-mc-squared
  [conf]
  (k-means-mc-2-initialization (add-default-chain-length conf)))