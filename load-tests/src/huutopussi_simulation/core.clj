(ns huutopussi-simulation.core
  (:require [clj-gatling.core :as gatling]
            [huutopussi-simulation.suite :as suite])
  (:gen-class))


(defn -main [& _]
  (gatling/run suite/dynamic-simulation
               {:concurrency 4
                :requests 20}))
