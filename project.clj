(defproject org.clojars.joshua/josh.meanings (or (System/getenv "PROJECT_VERSION") "3.0.14")
  :description "Clojure implementation of larger than memory K-Means clustering."
  :url "https://github.com/jcolechanged/josh.meanings"
  :license {:name "The MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.csv "1.0.1"]
                 [org.clojure/tools.cli "1.0.219"]
                 [babashka/fs "0.4.18"]
                 [techascent/tech.ml.dataset "7.021"]
                 [generateme/fastmath "2.2.1"]
                 [uncomplicate/clojurecl "0.15.1"]
                 [uncomplicate/neanderthal "0.46.0"]
                 [org.bytedeco/mkl-platform-redist "2022.2-1.5.8"]
                 [cnuernber/dtype-next "10.108"]
                 ;; Arrow support 
                 [org.apache.arrow/arrow-vector "6.0.0"
                  :exclusions [commons-codec/commons-codec
                               com.fasterxml.jackson.core/jackson-core
                               com.fasterxml.jackson.core/jackson-annotations
                               com.fasterxml.jackson.core/jackson-databind
                               org.slf4j/slf4j-api]]
                 [com.cnuernber/jarrow "1.000"]
                 [org.apache.commons/commons-compress "1.21"]
                 ;; Progress bars
                 [progrock "0.1.2"]
                 [bsless/clj-fast "0.0.11"]

                ;; Compression codecs
                ;; Required for decompressing lz4 streams with dependent blocks.

                 [org.lz4/lz4-java "1.8.0"]

                 ;; Logging support 
                 [ch.qos.logback/logback-classic "1.2.10" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.35"]
                 [org.slf4j/jcl-over-slf4j "1.7.35"]
                 [org.slf4j/log4j-over-slf4j "1.7.35"]
                 [com.taoensso/timbre "6.3.1"]
                 [net.java.dev.jna/jna "5.14.0"]
                 [com.github.luben/zstd-jni "1.5.5-11"]
                 [org.apache.parquet/parquet-hadoop "1.13.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.apache.hadoop/hadoop-common "3.3.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 ;; We literally need this for 1 POJO formatting object.
                 [org.apache.hadoop/hadoop-mapreduce-client-core  "3.3.0"
                  :exclusions [org.slf4j/slf4j-log4j12]]
                 [org.clojars.joshua/sampling "3.3"]]
  :plugins [[org.clojars.joshua/josh.benchmarking "0.0.4"]]
  :repl-options {:init-ns josh.meanings.kmeans}
  :source-paths ["src/clj" "src/kernels"]
  :profiles {:dev
             {:jvm-opts ["-Djdk.attach.allowAttachSelf"
                         "-XX:+TieredCompilation"
                         "-Xss20m"
                         "--add-modules" "jdk.incubator.foreign,jdk.incubator.vector"
                         "--enable-native-access=ALL-UNNAMED"
                         "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED"
                         "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
                         ]
              :global-vars {*warn-on-reflection* true
                            *unchecked-math* :warn-on-boxed}
              :dependencies [[criterium "0.4.6"]
                             [org.clojure/test.check "1.1.1"]
                             [com.clojure-goes-fast/clj-async-profiler "1.0.3"]
                             [com.clojure-goes-fast/clj-java-decompiler "0.3.4"]
                             [com.clojure-goes-fast/clj-memory-meter "0.2.2"]]}
             :notebook
             {:dependencies [[io.github.nextjournal/clerk "0.15.957"]]}}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :sign-releases false
                                    :username :env/clojars_username
                                    :password :env/clojars_password}]]
  :aot [josh.meanings.cli]
  :main josh.meanings.cli)
