# josh.meanings

[![CircleCI](https://circleci.com/gh/jColeChanged/josh.meanings.svg?style=shield&circle-token=a4b905e7d28f1f397566185359251b3d7d959818)](https://app.circleci.com/pipelines/github/jColeChanged/josh.meanings?filter=main) [![Clojars Project](https://img.shields.io/clojars/v/org.clojars.joshua/josh.meanings.svg)](https://clojars.org/org.clojars.joshua/josh.meanings)

A mean is a center of points. A means, a way of accomplishing 
that which is sought. Meaning, the latent concept vector that 
does not vary even though the words which express it might. 
This library though? The central idea behind it is to provide 
you a means of finding means so that you might come to know 
meanings through the process of repeatedly calculating means. 

This is a program for computing k-means in Clojure. 
It is built to handle workloads which involve datasets 
which are too large to fit in memory, but not so large that 
the computation cannot be persisted to disk. 

# Installation

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.joshua/josh.meanings.svg)](https://clojars.org/org.clojars.joshua/josh.meanings)

## Technical Details

 - Initialization is implemented as a multimethod whose 
   dispatch is chosen by the `:init` keyword. The following 
   initialization methods are supported.

   - `:niave`
   - `:k-means-++`
   - `:k-means-parallel`
   - `:k-mc-squared`
   - `:afk-mc`

   If you would prefer a different initialization scheme, you 
   can provide your own by adding a new `defmethod`.

   When calling the library the implementation will default to 
   `afk-mc` which is the assumption free approximation of 
   `k-means-++`. 

 - This library assumes that datasets will be medium data - larger than 
   memory, but smaller than disk. Callers must provide a reference to the 
   file which contains the dataset or a lazy sequence so that the dataset is 
   never fully realized in memory. The library leverages 
   `techascent.ml.dataset` to handle dataset serialization and 
   deserialization.

   Some file types aren't well suited to high performance 
   serialization and deserialization. The `:format` keyword controls 
   what file format will be used to persist data during the computation. 
   The list of supports formats are:

    - `:csv`
    - `:arrow`
    - `:arrows`
    - `:parquet`

   By default the `:parquet` format will be used.
  
 - Though it is possible to run k means a single time this is not 
   recommended. In general, k means doesn't find the globally optimal 
   solution. it is especially prone to finding bad clusters when run 
   using naive initialization, but even with k-means-++ and 
   k-means-parallel there is stochasticity. Multiple runs can converge 
   on different solutions. So it makes sense to run multiple times 
   and keep the best one.

   `k-means-seq` returns a lazy sequence of cluster results as well as 
   their cost. You can use it in various ways to ensure you get a good 
   clustering run - perhaps taking the first ten cluster attempts and 
   keeping the best one. Or perhaps something more sophisticated like 
   continuing to do clusteirng until you think the probability of finding 
   an improvmenet goes below some threshold.

 - Tested on both massive datasets and toy datasets. This is an area 
   that could use further improvement, since right now the tests are for 
   the happy path - the default number of runs, init, and format.

 - Which distance function should be used is left up to a multimethod protocol 
   that will dispatch based on the `:distance-fn` key. Special care 
   should be taken when choosing non-euclidean distances, because k-means 
   is not guaranteed to converge or stabilize with arbitrary distance 
   functions.