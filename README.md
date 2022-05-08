# meaning

A mean is a center of points. A means, a way of accomplishing 
that which is sought. Meaning, the latent concept vector that 
does not vary even though the words which express it might. 
This library though? The central idea behind it is to provide 
you a means of finding means so that you might come to know 
meanings through the process of repeatedly calculating means.

This is a program for computing `k-means` in Clojure. 
It is built to handle workloads which involve files 
which are larger than memory, but not so large that the computation 
cannot be persisted to disk. I hope that you have a painless 
experience working with larger than memory datasets by leveraging 
this program.

## Caveats 

Currently, only earth mover distance is supported as distance 
function.

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