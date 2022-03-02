(defproject huutopussi-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [compojure "1.6.1"]
                 [medley "1.3.0"]
                 [metosin/malli "0.6.2"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.clojure/core.async "1.3.610"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.0"]
                 [ring-cors "0.1.13"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler huutopussi-server.handler/prod-app}
  :uberjar-name "huutopussi-standalone.jar"
  :profiles {:reveal {:dependencies [[nrepl "0.8.3"]
                                     [nrepl-complete "0.1.0"]
                                     [vlaaad/reveal "1.2.186"]]
                      :repl-options {:nrepl-middleware [vlaaad.reveal.nrepl/middleware]}}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-jetty-adapter "1.8.2"]
                                  [ring/ring-mock "0.3.2"]]}})
