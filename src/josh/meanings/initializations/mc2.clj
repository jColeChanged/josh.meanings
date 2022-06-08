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
   [josh.meanings.persistence :as p])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))


;; In the paper they formulate sampling such that sampling is carried out 
;; one uniform sample at a time. I'm not going to do that. Instead I'm going 
;; to get one large sample as my first step. Doing this means we won't be 
;; doing both the CPU intensive and disk intensive parts of our algorithm 
;; at the same time.
(defn- samples
  "Get all the samples we'll need for the markov chain."
  [ds-seq k m]
  ;; 1 initial cluster, (k - 1) remaining clusters, each of which need 
  ;; to generate a markov chain of length m
  (uniform-sample ds-seq (+ 1 (* (- k 1) m))))

(defn- square
  [x]
  (* x x))

(defn- mcmc-sample
  "Perform markov chain monte carlo sampling to approxiate D^2 sampling"
  [distance-fn c rsp]

  ;; not special casing first selection because it decomplicates the 
  ;; inner loop code so that we're doing the same thing each time 
  ;; without requiring a wrapping let
  (loop [ps rsp
         dseq (map square (map (partial distance-fn c) rsp))
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

(defmethod initialize-centroids
  :k-mc-squared
  [conf]
  (log/info "Performing k-mc^2 initialization")
  (let [k (:k conf)   ;; number of clusters
        m (:m conf)   ;; markov chain length
        sp (samples (p/read-dataset-seq conf :points) k m)]
    (loop [c (first sp) cs [c] rsp (rest sp)]
      (log/info "Performing round of mcmc sampling")
      (if (empty? rsp)
        cs
        (let [nc (mcmc-sample (:distance-fn conf) c (take m rsp))]
          (log/info "Found nc" nc)
          (recur nc (conj cs nc) (drop m rsp)))))))

