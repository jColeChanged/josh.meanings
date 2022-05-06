# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.1.1] - 2022-05-06

### Changed

- Dramatically improved performance on large datasets accomplished by switching 
from buffered csv reading to optimized tech.ml.dataset usage.

## [0.1.0] - 2022-05-01

### Added

- Initial project created with support for k-means clustering on larger than memory datasets.
- Only configuration provided is choice of k. Distance function used is earth mover distance.


