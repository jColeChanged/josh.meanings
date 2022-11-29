# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.3.1] - 2022-11-28

 - Updated documentation to reflect usage.
 - Improved ease of use - defaults for chain length chosen automatically when not provided.
 - Expanded test coverage with generative testing.
 - Added support for saving and loading learned models.
 - Assignments when calling k-means-seq no longer overwrite each other.
 - Add .load-assignments and .classify to ClusterResult.

## [0.1.5] - 2022-06-08

 - Now support the mc^2 initialization method.
 - Now support the afk mc^2 initialization method.
 
## [0.1.4] - 2022-05-28

 - Default calling convention now runs multiple instances of k means 
   clustering.
 - k-means can now be called with an option to specify which distance 
   function to use. 
 - k-means now supports many different distance functions.

## [0.1.3] - 2022-05-25

 - Now supporting initialization via k-means++.
 - Now supporting initialization via k-means||.
 - Now supporting initialization via k-means||.
 - Now supporting initialization via naive uniform sampling.
 - k-means can now be called with options to determine the initialization method.
 - Now supporting parquet format.
 - Now supporting arrow format.
 - Now supporting arrows format.
 - k-means can now be called with options to determine preferred file format.
 - Added logs to help make progress of computations more obvious.
 - Added the `initialize-centroids` multimethod; choice of initialization method can now be controlled by callers via multimethods.
 - Now support calling k-means with lazyseqs which are ->dataset compatible.

## [0.1.2] - 2022-05-09

- Fixed an issue with processing large datasets by moving from arrow files to arrows IPC format.
- Rebranded from clj-kmeans to josh.meanings, because I like the name meanings but 
figure I should namespace it.

## [0.1.1] - 2022-05-06

### Changed

- Dramatically improved performance on large datasets accomplished by switching 
from buffered csv reading to optimized tech.ml.dataset usage.

## [0.1.0] - 2022-05-01

### Added

- Initial project created with support for k-means clustering on larger than memory datasets.
- Only configuration provided is choice of k. Distance function used is earth mover distance.


