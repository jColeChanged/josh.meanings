# Change Log

All notable changes to this project will be documented in this file. 
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
For releases before version [1.0.0] this project *did not* follow [semver](https://semver.org/).  
For releases after version 1.0.0 this project *will* follow semver.

## [0.3.4] - 2022-12-03

## Fixed 

 - Fixes a bug which could cause incorrect assignment of points to clusters.
 - Expands unit test coverage.

## [0.3.1] - 2022-11-28

## Added 

 - Added support for saving and loading learned models.
 - Assignments when calling k-means-seq no longer overwrite each other.
 - Add .load-assignments and .classify to ClusterResult.

## Changed

 - Updated documentation to reflect usage.
 - Improved ease of use - defaults for chain length chosen automatically when not provided.
 - Expanded test coverage with generative testing.


## [0.1.5] - 2022-06-08

## Added

 - Now support the mc^2 initialization method.
 - Now support the afk mc^2 initialization method.
 
## [0.1.4] - 2022-05-28

## Added

 - k-means can now be called with an option to specify which distance 
   function to use. 
 - k-means now supports many different distance functions.

## Changed

 - Default calling convention now runs multiple instances of k means 
   clustering.

## [0.1.3] - 2022-05-25

## Added

 - Now supporting initialization via k-means++.
 - Now supporting initialization via k-means||.
 - Now supporting initialization via k-means||.
 - Now supporting initialization via naive uniform sampling.
 - k-means can now be called with options to determine the initialization method.
 - Now supporting parquet format.
 - Now supporting arrow format.
 - Now supporting arrows format.
 - Added logs to help make progress of computations more obvious.
 - Added the `initialize-centroids` multimethod; choice of initialization method can now be controlled by callers via multimethods.
 - Now support calling k-means with lazyseqs which are ->dataset compatible.
 - k-means can now be called with options to determine preferred file format.

## [0.1.2] - 2022-05-09

## Fixed

- Fixed an issue with processing large datasets by moving from arrow files to arrows IPC format.

## Changed

- Rebranded from clj-kmeans to josh.meanings, because I like the name meanings but figure I should namespace it.

## [0.1.1] - 2022-05-06

### Changed

- Dramatically improved performance on large datasets accomplished by switching 
from buffered csv reading to optimized tech.ml.dataset usage.

## [0.1.0] - 2022-05-01

### Added

- Initial project created with support for k-means clustering on larger than memory datasets.
- Only configuration provided is choice of k. Distance function used is earth mover distance.


