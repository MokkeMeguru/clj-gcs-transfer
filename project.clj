(defproject clj-gcs-transfer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.google.cloud/google-cloud-storage "1.113.14"]
                 [com.taoensso/timbre "5.1.2"]
                 [orchestra "2021.01.01-1"]
                 [environ "1.2.0"]

                 [org.clojure/tools.cli "1.0.206"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [metosin/reitit "0.5.10"]
                 [metosin/reitit-swagger "0.5.10"]
                 [metosin/reitit-swagger-ui "0.5.10"]]

  :main ^:skip-aot clj-gcs-transfer.core
  :plugins [[lein-environ "1.1.0"]
            [cider/cider-nrepl "0.25.4"]
            [lein-codox "0.10.7"]
            [refactor-nrepl "2.5.0"]]
  :codox {:output-path "doc"
          :metadata {:doc/format :markdown}}
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
