(defproject clj-kmeans "0.1.0-SNAPSHOT"
  :description "Clojure implementation of K-Means clustering with support for larger than memory datasets."
  :url "https://github.com/jcolechanged/clj-kmeans"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.csv "1.0.1"]
                 [techascent/tech.ml.dataset "6.085"]
                 [org.clojure/tools.cli "1.0.206"]
                 [generateme/fastmath "2.1.8"]]
  :main clj-kmeans.core
  :jvm-opts ["-Xmx1G"]
  :repl-options {:init-ns clj-kmeans.core})
