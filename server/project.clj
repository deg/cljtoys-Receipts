(defproject receipts-server "0.0.1-SNAPSHOT"
  :description "Server side of the Receipts app"
  :url "https://github.com/deg/cljtoys-Receipts"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [io.pedestal/pedestal.service "0.5.2" :exclusions [cheshire org.clojure/core.async]]
                 [com.datomic/datomic-pro "0.9.5561" :exclusions [com.fasterxml.jackson.core/jackson-core
                                                                  com.fasterxml.jackson.core/jackson-databind
                                                                  joda-time
                                                                  org.slf4j/slf4j-nop]]
                 [com.cognitect/pedestal.vase "0.9.2-SNAPSHOT" :exclusions [com.datomic/datomic-free]]

                 ;; Remove this line and uncomment one of the next lines to
                 ;; use Immutant or Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.5.2"]
                 ;; [io.pedestal/pedestal.immutant "0.5.2-SNAPSHOT"]
                 ;; [io.pedestal/pedestal.tomcat "0.5.2-SNAPSHOT"]

                 [ch.qos.logback/logback-classic "1.2.3" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]

                 ;;; Time library
                 [clj-time "0.13.0"]

                 ;;; Parse/generate CSV
                 [clojure-csv/clojure-csv "2.0.2"]]
  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  ;; If you use HTTP/2 or ALPN, use the java-agent to pull in the correct alpn-boot dependency
  ;:java-agents [[org.mortbay.jetty.alpn/jetty-alpn-agent "2.0.3"]]
  :profiles {:dev {:aliases {"run-dev" ["trampoline" "run" "-m" "receipts-server.server/run-dev"]}
                   :dependencies [[io.pedestal/pedestal.service-tools "0.5.2"]]}
             :uberjar {:aot [receipts-server.server]}}
  :main ^{:skip-aot true} receipts-server.server)
