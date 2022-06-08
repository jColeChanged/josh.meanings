(ns josh.meanings.initializations.afk-mc2
  "Fast and Provably Good Seedings for k-Means is a paper by Olivier Bachem, 
   Mario Lucic, S. Hamed Hassani, and Andreas Krause which introduces an 
   improvement to the monte carlo markov chain approximation of k-means++ 
   D^2 sampling. It accomplishes this by computing the D^2 sampling 
   distribution with respect to the first cluster. This has the practical 
   benefit of removing some of the assumptions, like choice of distance 
   metric, which were imposed in the former framing. As such the name of 
   this algorithm is assumption free k-mc^2. A savvy reader may note that 
   by computing the D^2 sampling distribution as part of the steps this 
   algorithm loses some of the theoretical advantages of the pure markov 
   chain formulation. The paper argues that this is acceptable, because 
   in practice computing the first D^2 sampling distribution ends up paying 
   for itself by reducing the chain length necessary to get convergence 
   guarantees."
  (:require
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as p])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))


(defn square [x] (* x x))

(defn q-of-x
  "Computes the q(x) distribution for all x in the dataset."
  [conf cluster]
  (let [dx-squared (comp square (partial (:distance-fn conf cluster)))
        regularization-term (/ 1 (* 2 (reduce + (map count (p/read-dataset-seq conf :points)))))
        d2-sum (->> (p/read-dataset-seq conf :points)
                 (map dx-squared)
                 (map (partial reduce +))
                 (reduce +))
        ;; instead of multiplying 1/2 by each refactoring the /2 into true-d2
        doubled-d2-sum (* 2 d2-sum)
        qx #(+ (/ (dx-squared %) doubled-d2-sum) regularization-term)

    

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
  :afk-mc
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

