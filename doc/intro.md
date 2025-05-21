# josh.meanings

## Initialization

Initialization is implemented as a multimethod whose 
dispatch is chosen by the `:init` keyword. The following 
initialization methods are supported.

 - `:naive`
 - `:k-means-++`
 - `:k-means-parallel`
 - `:k-mc-squared`
 - `:afk-mc`

If you would prefer a different initialization scheme, you 
can provide your own by adding a new `defmethod`.

When calling the library the implementation will default to 
`afk-mc` which is the assumption free approximation of 
`k-means-++`. 

## Dataset Format

This library assumes that datasets will be medium data - larger than 
memory, but smaller than disk. Callers must provide a reference to the 
file which contains the dataset or a lazy sequence so that the dataset is 
never fully realized in memory. The library leverages 
`techascent.ml.dataset` to handle dataset serialization and 
deserialization.

Some file types aren't well suited to high performance 
serialization and deserialization. The `:format` keyword controls 
what file format will be used to persist data during the computation. 
The list of supported formats are:

 - `:csv`
 - `:arrow`
 - `:arrows`
 - `:parquet`

By default the `:parquet` format will be used.

## Distance Functions

Which distance function should be used is left up to a multimethod protocol 
that will dispatch based on the `:distance-fn` key. Special care 
should be taken when choosing non-euclidean distances, because k-means 
is not guaranteed to converge or stabilize with arbitrary distance 
functions.
   
The `get-distance-fn` multimethod dispatches based on identity to 
determine what distance function to use when calculating distances. 
By default the supported distance function keys are:

- :euclidean
- :manhattan
- :chebyshev
- :correlation
- :canberra
- :emd
- :euclidean-sq
- :discrete
- :cosine
- :angular
- :jensen-shannon

The default distance function is :emd which may not be appropriate for all
use cases since it doesn't minimize the variational distance like euclidean
would.  If you don't know why :emd is the default you should probably switch 
to using :euclidean.

## Calling Options

Though it is possible to run k means a single time this is not 
recommended. In general, k means doesn't find the globally optimal 
solution. It is especially prone to finding bad clusters when run
using naive initialization, but even with k-means-++ and 
k-means-parallel there is stochasticity. Multiple runs can converge 
on different solutions. So it makes sense to run multiple times 
and keep the best one.

`k-means-seq` returns a lazy sequence of cluster results as well as 
their cost. You can use it in various ways to ensure you get a good 
clustering run - perhaps taking the first ten cluster attempts and 
keeping the best one. Or perhaps something more sophisticated like 
continuing to do clustering until you think the probability of finding 
an improvement goes below some threshold.


## Example Usage

```
(k-means "/home/joshua/Projects/fast.parquet" 200 :columns ["f1" "f2" "f3"])
```

## Testing

Tested on both massive datasets and toy datasets. This is an area 
that could use further improvement, since right now the tests are for 
the happy path - the default number of runs, init, and format.

To run unit tests use `lein test`.
