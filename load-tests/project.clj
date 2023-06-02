(defproject huutopussi-simulation "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [hato "0.9.0"]
                 [org.clojure/tools.logging "1.2.4"]
                 [org.apache.logging.log4j/log4j-api "2.19.0"]
                 [org.apache.logging.log4j/log4j-core "2.19.0"]
                 [cheshire "5.11.0"]
                 [com.github.mhjort/trombi "1.0.0"]
                 [com.github.mhjort/trombi-gatling-highcharts-reporter "1.0.0"]]
  :main huutopussi-simulation.core)
