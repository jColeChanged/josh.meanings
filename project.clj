(defproject jcolechanged/josh.meanings "0.1.1-SNAPSHOT"
  :description "Clojure implementation of larger than memory K-Means clustering."
  :url "https://github.com/jcolechanged/josh.meanings"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.csv "1.0.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [techascent/tech.ml.dataset "6.086"]
                 [org.clojure/tools.cli "1.0.206"]
                 [generateme/fastmath "2.1.8"]
                 [org.apache.arrow/arrow-vector "6.0.0"]
                 ;; Compression codecs
                 [org.lz4/lz4-java "1.8.0"]
                 ;; Required for decompressing lz4 streams with dependent blocks.
                 [net.java.dev.jna/jna "5.10.0"]
                 [com.github.luben/zstd-jni "1.5.1-1"]]
  :main josh.meanings.core
  :jvm-opts ["-Xmx2g"]
  :repl-options {:init-ns josh.meanings.core})
