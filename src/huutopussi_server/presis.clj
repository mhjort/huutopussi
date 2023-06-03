(ns huutopussi-server.presis
  (:require [clojure.core.async :as async :refer [<!]]))

(defn- find-first-available-match [])
(defn- team-full? [_])
(defn- start-game [])
(defn- wait-for-other-players [])
(def card "A")
(defn- send-as-json-via-http [_])
(defn- valid? [_])
(defn- update-game-state [_])
(def client-ch [])
(def server-ch [])
(defn- update-view [_])
(defn- tick [_ _])
(def world-state (atom {}))

(let [match (find-first-available-match)]
  (if (team-full? match)
    (start-game)
    (wait-for-other-players)))

;client (clojurescript)
(send-as-json-via-http {:play-card card})
(let [new-state (<! server-ch)]
  (update-view new-state))

;server (clojure jvm)
(let [action (<! client-ch)]
  (when (valid? action)
    (update-game-state action)))

;server
(loop []
  (let [action (<! client-ch)
        updated-model (tick (:model @world-state)
                            action)]
        (swap! world-state assoc :model updated-model)
        (when-not (:game-ended? updated-model)
          (recur))))

(defn play-card-as-bot [game-model]
  (first (:possible-cards game-model)))
