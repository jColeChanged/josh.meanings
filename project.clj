(defproject org.clojars.joshua/josh.meanings (or (System/getenv "PROJECT_VERSION") "SNAPSHOT-0.1.0")
  :description "Clojure implementation of larger than memory K-Means clustering."
  :url "https://github.com/jcolechanged/josh.meanings"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.csv "1.0.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.clojure/test.check "1.1.1"]
                 [techascent/tech.ml.dataset "6.086"]
                 [org.clojure/tools.cli "1.0.206"]
                 [generateme/fastmath "2.1.8"]
                 [org.apache.arrow/arrow-vector "6.0.0"]
                 ;; Compression codecs
                 [org.lz4/lz4-java "1.8.0"]
                 ;; Required for decompressing lz4 streams with dependent blocks.
                 [net.java.dev.jna/jna "5.10.0"]
                 [com.github.luben/zstd-jni "1.5.1-1"]
                 [org.apache.parquet/parquet-hadoop "1.12.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.apache.hadoop/hadoop-common "3.3.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 ;; We literally need this for 1 POJO formatting object.
                 [org.apache.hadoop/hadoop-mapreduce-client-core  "3.3.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.clojars.joshua/sampling "3.3"]
                 [criterium "0.4.6"]]
  :main josh.meanings.core
  :jvm-opts ["-Xmx2g"
             "-XX:+TieredCompilation"]
  :plugins [[org.clojars.joshua/josh.benchmarking "0.0.4"]]
  :repl-options {:init-ns josh.meanings.core}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]])
