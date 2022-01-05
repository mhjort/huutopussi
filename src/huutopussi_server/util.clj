(ns huutopussi-server.util
  (:require [huutopussi-server.schemas :as schemas]
            [clojure.walk :as walk]
            [clojure.pprint :refer [pprint]]))

(defn pretty-print [m]
  (with-out-str (pprint m)))

(defn generate-players [teams cards-per-player]
  (schemas/validate-schema schemas/Teams teams)
  (let [[team1-first-player
         team1-second-player
         team2-first-player
         team2-second-player] (mapcat #(-> % val :players) teams)
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

(defn select-next-player-id [current-index players]
  (let [possible-index (inc current-index)
        next-player-index (if (= (count players) possible-index)
                            0
                            possible-index)
        next-player (first (filter #(= next-player-index (:player-index %)) (vals players)))]
    (:player-id next-player)))

(defn team-mate-for-player [player-id teams]
  (->> (filter #(some #{player-id} (:players %)) (vals teams))
       (first)
       (:players)
       (remove #{player-id})
       (first)))

(defn teams-by-player [teams]
  (reduce-kv (fn [m team {:keys [players]}]
               (let [[p1 p2] players]
                 (assoc m p1 team p2 team)))
             {}
             teams))

(defn map-vals [f m]
  (reduce-kv #(assoc %1 %2 (f %3)) {} m))

(defn- player-name-by-id [match id]
  (if-let [player-name (get-in match [:players id :name])]
    player-name
    (throw (ex-info "Could not find player name by id" {:id id}))))

(defn replace-player-ids-with-player-names [match game-model]
  ;This is a security feature. Internally model uses player ids that also act as api keys
  (walk/prewalk
    (fn [form]
      (cond
        (:players form) (update form :players #(map (partial player-name-by-id match) %))
        (:player form) (update form :player (partial player-name-by-id match))
        (:next-player-name form) (update form :next-player-name (partial player-name-by-id match))
        (:target-player form) (update form :target-player (partial player-name-by-id match))
        :else form))
    game-model))
