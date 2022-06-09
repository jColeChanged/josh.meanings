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
   [tech.v3.dataset :as ds]
   [clojure.tools.logging :as log]
   [josh.meanings.persistence :as p])
  (:use
   [josh.meanings.initializations.core]
   [josh.meanings.initializations.utils]))



(defn- square [x] (* x x))


(defn- point
  [row]
  (butlast row))

(defn- qx
  [row]
  (last row))

(defn- q-of-x
  "Computes the q(x) distribution for all x in the dataset."
  [conf cluster]
  (log/info "Computing q(x) distribution with respect to " cluster)
  (let [dx-squared (comp square (partial (:distance-fn conf) cluster))
        regularization-term (/ 1 (* 2 (reduce + (map ds/row-count (p/read-dataset-seq conf :points)))))
        d2-sum (->> (p/read-dataset-seq conf :points)
                    (map ds/rowvecs)
                    (map (partial map dx-squared))
                    (map (partial reduce +))
                    (reduce +))
        ;; instead of multiplying 1/2 by each refactoring the /2 into true-d2
        doubled-d2-sum (* 2 d2-sum)
        qx (fn [& cols] (+ (/ (dx-squared cols) doubled-d2-sum) regularization-term))]
    (p/write-dataset-seq conf :points
                         (->> (p/read-dataset-seq conf :points)
                              (map #(ds/column-map % "q(x)" qx))))))

(defn- cleanup-q-of-x
  "Removes q(x) distribution for all x in the dataset."
  [conf]
  (p/write-dataset-seq conf :points
                       (->> (p/read-dataset-seq conf :points)
                            (map #(dissoc % "q(x)")))))



;; In the paper they formulate sampling such that sampling is carried out 
;; one weighted sample at a time. I'm not going to do that. Instead I'm going 
;; to get one large sample. Doing this means we won't be doing both the CPU 
;; intensive and disk intensive parts of our algorithm at the same time.
(defn- samples
  "Get all the samples we'll need for the markov chain."
  [ds-seq k m]
  (log/info "Sampling with respect to q(x)")
  (let [sample-count (* (- k 1) m)]
    (weighted-sample ds-seq qx sample-count)))


(defn- mcmc-sample
  "Perform markov chain monte carlo sampling to approxiate D^2 sampling"
  [distance-fn c rsp]
  (loop [points (map point rsp)                         ;; the points 
         dyqyseq  (map *                                ;; d(c, y) * q(y)
                       (map (partial distance-fn c) points)
                       (map qx rsp))
         rands (repeatedly (count points) rand)         ;; Unif(0, 1)
         x (first points)                               ;; x
         dxqx (first dyqyseq)]                          ;; d(c, x) * q(x)
    (if (empty? points)
      x
      (let [take (or (zero? dxqx) (> (/ (first dyqyseq) dxqx) (first rands)))]
        (recur
         (rest points)
         (rest dyqyseq)
         (rest rands)
         (if take (first points) x)
         (if take (first dyqyseq) dxqx))))))

(defn- k-means-assumption-free-mc-initialization
  [conf]
  (log/info "Performing afk-mc initialization")
  (log/info "Sampling cluster from dataset for initial centroid choice")
  (let [cluster (first (uniform-sample (p/read-dataset-seq conf :points) 1))]
    (log/info "Got initial cluster" cluster)
    (q-of-x conf cluster)
    (let [k (:k conf)   ;; number of clusters
          m (:m conf)   ;; markov chain length
          sp (samples (p/read-dataset-seq conf :points) k m)
          clusters
          (loop [c cluster cs [cluster] rsp sp]
            (log/info "Performing round of mcmc sampling")
            (if (empty? rsp)
              cs
              (let [nc (mcmc-sample (:distance-fn conf) c (take m rsp))]
                (recur nc (conj cs nc) (drop m rsp)))))]
      (cleanup-q-of-x conf)
      clusters)))

(defmethod initialize-centroids
  :afk-mc
  [conf]
  (centroids->dataset conf (k-means-assumption-free-mc-initialization conf)))