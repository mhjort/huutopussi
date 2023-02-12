(defproject huutopussi-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [compojure "1.7.0"]
                 [medley "1.4.0"]
                 [metosin/malli "0.10.1"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.clojure/core.async "1.6.673"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [ring/ring-ssl "0.3.0"]
                 [ring/ring-defaults "0.3.4"]
                 [ring/ring-json "0.5.1"]
                 [ring/ring-devel "1.9.6"]
                 [ring-cors "0.1.13"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler huutopussi-server.handler/prod-app}
  :uberjar-name "huutopussi-standalone.jar"
  :profiles {:reveal {:dependencies [[nrepl "1.0.0"]
                                     [nrepl-complete "0.1.0"]
                                     [vlaaad/reveal "1.3.280"]]
                      :repl-options {:nrepl-middleware [vlaaad.reveal.nrepl/middleware]}}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-jetty-adapter "1.9.6"]
                                  [ring/ring-mock "0.4.0"]]}})
