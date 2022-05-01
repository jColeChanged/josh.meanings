# clj-kmeans

A Clojure CLI application designed to perform K means classification 
using the earth mover distance function on datasets which are so large 
that they can't fit into memory, but which are still small enough that 
they can fit on disk.

## Usage

```
lein build
lein run -- -i input.csv -k 5
```

