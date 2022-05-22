(ns josh.meanings.simplifying
  "Simplification in this context means transforming a 
   dataset that has duplicate values to one in which we 
   there are no duplicates and one new column - the number 
   of duplicates that were observed."
  (:require
     [tech.v3.dataset :as ds]))

;; In the real world when we get data there are often 
;; a lot of duplicate entries. K means clustering goes 
;; over the entire dataset multiple times. If we can 
;; massivley reduce the size of the dataset then we can 
;; avoid having to do an immense number of iterations.
;; 
;; This comes at a cost. We have to do a pass over the 
;; dataset. This pass is going to take roughly O(n*c) 
;; to perform the mapping step of the count and then 
;; O(log(u) * s) where u is the number of splits and s is 
;; the number of times of mapped data partitions. The 
;; combined algorithm is therefore going to be something 
;; like O(nc + log(u)s). Obviously we can know that in the 
;; very worst case n = u. So that means it is
;; O(nc + log(n)s). s is going to be a function of n. 
;; Basically some very large constant divided by n. 
;; 
;; So this step makes a lot of sense only if the data 
;; has a decent fraction of non-unique entries.

(defn ds-seq->frequencies
  [ds-seq]
  (apply merge-with + (map frequencies (map ds/rowvecs ds-seq))))