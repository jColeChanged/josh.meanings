# Change Log

All notable changes to this project will be documented in this file. 
This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
For releases before version [1.0.0] this project *did not* follow [semver](https://semver.org/).  
For releases after version 1.0.0 this project *will* follow semver, with the addition that minor 
releases might only add additional tests and/or documentation updates rather than bug fixes.

## [13.0.4] - 2023-12-26

This releases gets working support for GPU backed emd calculations up 
to <256 categories.  It supports dataset sizes that other k means 
implementations I've tried fail to support.  It is also faster than 
most other k means implementations I've tried in part because it uses 
a better algorithm than k-means++.

However, if you aren't on the happy path of EMD and k-means-afk this 
release probably breaks everything.

## [1.0.0] - 2022-12-04

This release improves the usability of loading and saving clustered models.
It is potentially backward incompatible with previous releases because it 
changes the returned output of the cluster result record.

## Added

 - `ClusterResult` now provides an `:assignments` field which is a filepath to an assignments dataset.
 - `ClusterResult` now provides a `.load-centroids` method.

## Changed

 - `ClusterResult` now has a filepath rather than a `tech.ml.dataset` under the `:centroids` key.
 - `ClusterResult` `:configuration` field now contains a map rather than the full clustering state record.  
    This map contains the following configuration fields: `:k`, `:m`, `:distance-key`, `:init` and `:col-names`.
 -  `ClusterResult` `.load-assignments` now returns a sequence of sequences.
 -  The `ClusterResult` `classify` method now supports dynamic dispatch.  When given a vector it will assume that 
   vector has fields in `:col-name` ordering.  When given a map, it will extract fields in `:col-name` ordering 
   before calling classify.

## Fixed

  - The `load-model` function would fail to load persisted cluster results from disk because when 
    printed to a file the output of a dataset would have pretty printing which conflicted with the 
    reader syntax.
  - Calling `.load-assignments` on a massive cluster result should no longer triggers out of memory 
    issues.

## Testing

 - Unit testing added for `.save-model`.
 - Unit testing added for `load-model`.
 - Unit testing added for `.classify`.
 - Unit testing added for `.load-assignments`.
 - Unit testing added for `.load-centroids`.
 - Unit testing added for the `ClusterResult` `:configuration` field validity.


## [0.3.4] - 2022-12-03

## Fixed

 - Expands unit test coverage.
 - Fixes a bug which could cause keywords in datasets to trigger clustering failures.

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


