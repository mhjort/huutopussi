(ns huutopussi-server.util
  (:require [malli.core :as m]
            [clojure.pprint :refer [pprint]]))

(defn pretty-print [m]
  (with-out-str (pprint m)))

(def Teams
  ;Note! Only exactly 2 teams with both having 2 players is supported
  [:map-of :keyword [:repeat {:min 2 :max 2} string?]])

(defn generate-players [teams cards-per-player]
  (when-not (m/validate Teams teams)
    (throw (ex-info "Malli validation failed" {:error (m/explain Teams teams)})))
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
