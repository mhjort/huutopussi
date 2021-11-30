(ns huutopussi-server.util
  (:require [clojure.pprint :refer [pprint]]))

(defn pretty-print [m]
  (with-out-str (pprint m)))
(defn generate-players [teams cards-per-player]
  (let [[team1-first-player team1-second-player team2-first-player team2-second-player] (mapcat val teams)
        player-ids [team1-first-player team2-first-player team1-second-player team2-second-player]]
    (into {} (map (fn [[player-index player-id] cards]
                    (let [hand-cards (vec cards)]
                      [player-id {:player-id player-id
                                  :player-index player-index
                                  :hand-cards hand-cards
                                  :possible-cards []
                                  :possible-actions []}]))
                  (map-indexed vector player-ids)
                  cards-per-player))))

(defn team-mate-for-player [player-id teams]
  (->> (filter #(some #{player-id} %) (vals teams))
       (first)
       (remove #{player-id})
       (first)))
