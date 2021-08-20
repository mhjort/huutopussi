(defproject huutopussi-client.core "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [cljs-http "0.1.46"]
                 [com.cemerick/url "0.1.1"]
                 [org.clojure/core.async "1.3.610"]
                 [reagent "1.0.0-alpha2"]
                 [re-frame "1.1.1"]]

  :source-paths ["src"]
  :resource-paths ["resources" "target"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "huutopussi-beacon.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.9"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   }})

