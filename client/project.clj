(defproject huutopussi-client.core "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.57"]
                 [cljs-http "0.1.46"]
                 [com.cemerick/url "0.1.1"]
                 [org.clojure/core.async "1.5.648"]
                 [reagent "1.1.1"]
                 [cljsjs/react "17.0.2-0"]
                 [cljsjs/react-dom "17.0.2-0"]
                 [re-frame "1.2.0"]]

  :source-paths ["src"]
  :resource-paths ["resources" "target"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "huutopussi-beacon.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.18"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   }})

