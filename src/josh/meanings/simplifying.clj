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
;; O(log(s) * u) where u is the number of unique items and 
;; s is the number of mapped partitions.
;; 
;; So this step makes a lot of sense if the data 
;; has a decent fraction of non-unique entries but it is 
;; a waste of time if it isn't.
;;
;; This introduces a risk for very large datasets that u
;; is larger than the system memory. My current implementation 
;; doesn't try to address that. A future version ought to.

(defn ds-seq->frequencies
  [ds-seq]
  (apply merge-with + (map frequencies (map ds/rowvecs ds-seq))))