# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [0.1.2] - 2022-05-09

- Fixed an issue with processing large datasets by moving from arrow files to arrows
IPC format.
- Rebranded from clj-kmeans to josh.meanings, because I like the name meanings, but 
figure I should namespace it, and I hope to eventually make this tool widely available 
so that others don't need to get stuck at the same places I did.

## [0.1.1] - 2022-05-06

### Changed

- Dramatically improved performance on large datasets accomplished by switching 
from buffered csv reading to optimized tech.ml.dataset usage.

## [0.1.0] - 2022-05-01

### Added

- Initial project created with support for k-means clustering on larger than memory datasets.
- Only configuration provided is choice of k. Distance function used is earth mover distance.


