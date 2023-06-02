(ns huutopussi-simulation.core
  (:require [trombi.core :as trombi]
            [huutopussi-simulation.suite :as suite])
  (:gen-class))


(defn -main [& _]
  (trombi/run suite/dynamic-simulation
               {:concurrency 4
                :requests 20}))
