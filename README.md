# josh.meanings

[![CircleCI](https://circleci.com/gh/jColeChanged/josh.meanings.svg?style=svg)](https://circleci.com/gh/jColeChanged/josh.meanings.svg?style=svg)

This is a program for computing k-means in Clojure.  It is built to handle workloads which are medium data, 
which means they involve datasets which are too large to fit in memory, but not so large that the computation 
cannot be persisted to disk.

Unlike most other K-means implementations we employ several techniques which lend themselves toward making this 
K-means implementation quite a bit faster than other implementations.

1. We leverage memory mapping of the datasets.
2. We do our distance calculations on the GPU.
3. We implement initialization schemes from more recent research.

> [!NOTE]
> GPU acceleration is available for several distance functions including EMD,
> Euclidean, Manhattan, Chebyshev and Euclidean squared.

# Installation

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.joshua/josh.meanings.svg)](https://clojars.org/org.clojars.joshua/josh.meanings)

## Getting Started

```
(require '[josh.meanings.kmeans :refer [k-means k-means-seq]]
         '[josh.meanings.protocols.savable :refer [save-model]]
         '[josh.meanings.protocols.classifier :refer [classify load-centroids load-assignments]])


;; Get a dataset.  You can pass in your dataset under a variety of formats. 
;; See the docs for more details on supported formats.
(def dataset "your_dataset.csv")  

;; Choose the number of clusters you want
(def k 10)


;; To get a single cluster model
(def model (k-means dataset k))

;; Alternatively you can run k means multiple times.  This is recommended because 
;; some k means initializations don't give guarantees on the quality of a solution 
;; and so you can get better results by running k means multiple times and taking 
;; the best result.
(def model (apply min-key :cost (take k-tries (k-means-seq cluster-dataset-name k))))

;; Once you have a model you can save it.
(def model-path (.save-model model))

;; Later you can load that model
(def model (load-model model-path))

;; To load the assignments just
(.load-assignments model)

;; To classify a new entry
(.classify model [1 2 3])

;; To view the centroids
(.load-centroids model)
```

## Testing

Run the project's unit tests with:

```
lein test
```

Tests exercising the GPU code paths require an Nvidia GPU with CUDA support.

## License

Distributed under the terms of the [MIT License](LICENSE).
