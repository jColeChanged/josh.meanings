# meaning

[![CircleCI](https://circleci.com/gh/jColeChanged/josh.meanings.svg?style=shield&circle-token=a4b905e7d28f1f397566185359251b3d7d959818)](https://app.circleci.com/pipelines/github/jColeChanged/josh.meanings?filter=main)

A mean is a center of points. A means, a way of accomplishing 
that which is sought. Meaning, the latent concept vector that 
does not vary even though the words which express it might. 
This library though? The central idea behind it is to provide 
you a means of finding means so that you might come to know 
meanings through the process of repeatedly calculating means.

This is a program for computing `k-means` in Clojure. 
It is built to handle workloads which involve datasets 
which are too large to fit in memory, but not so large that 
the computation cannot be persisted to disk. I hope that you 
have a painless experience working with larger than memory 
datasets by leveraging this program.

## Technical Details (God willing)

[x] Uses `k-means||` initialization.

[x] Uses `parquet` to optimize disk access.

[ ] Leverages Clojure's `transducers` to use all cores.

[ ] Uses __GPUs__ to optimize matrix math when available.
  
[ ] Runs multiple times to prevent unlucky local mimina. 
  
[ ] Emits improvements as events to enable reactive programming.

[x] Tested on both massive datasets and toy datasets.

[ ] Supports choice of distance function, but defaults to EMD.

## Caveats

 - Only intended to handle 1D clustering.

## Comparison

I wrote this because most of the other k means implementations 
I tried using failed me horribly. sklearn couldn't handle larger 
than memory datasets. dask.ml claimed to be able to solve that 
problem, but it fell over when given a large dataset because it 
broke the problem into more discerete subtasks than could fit in 
memory. A C++ program which promises to be a fast k-means implementation 
that could handle larger than memory files worked great as long as 
you gave it files that could fit in memory and while in theory I could 
have converted it to use it stxll in practice I did do so, but gave 
it up as the wrong approach. Anyone who actually tries working on 
larger than memory datasets is likely aware that a niave k means 
implementation isn't the right path. Most libraries on GitHub 
don't implement a k means that has good theoretical properties, but 
niave k means, which takes far longer in both theory and practice 
than other initialization methods while also having the joyous 
property of having no guarantee of performance such that the path 
to getting a good k means is the same path as getting a good sort 
with bogo sort: keep trying until you get lucky. There were some 
k means implementations which did do a good job. Spark was lovely. 
However, I wanted to use arbitrary distance functions and for some 
strange reason Spark didn't want to let me do that. So here we are.
A library built and ready for you to do actual k means computation 
and I have no desire whatsoever to go through the shit show that was 
trying to use all these other solutions. If you are less burnt out 
then I am, please, submit a pull request that compares the performance 
of this k means implementation with others. I'm interested in these 
comparisons:

1. First and most importantly, does it work when you give it files that 
are larger than RAM.

2. What is the wallclock time for getting good clusters.

3. Which library finds the clusters which best minimize the objective.

## Usage

You can input data using either:

```
lein run -- --input input.csv --k 5
```

Or alternatively you can pass in an arrow file.

```
lein run -- --input input.arrow --k 5
```

After execution you will be left with several files. The 
history.input.csv file will contain information about training 
runs like the number of iterations that were performed. 
assignments.input.arrow which will contain the assingments for 
each points. Finally, centroids.input.csv with which you can 
use for the purpose of classification.
