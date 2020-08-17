(ns beacon-server.game
  (:require [beacon-server.model.deck :as deck]))



(let [match {:id "1" :players [{:name "A"}
                               {:name "B"}
                               {:name "C"}
                               {:name "D"}]}
      shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))]
  (update match :players #(map (fn [player cards]
                                 (assoc player :cards cards)) % shuffled-cards)))

(defn start [matches id]
  (let [match (get @matches id)
        shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
        players-with-cards (map (fn [player cards]
                                  (assoc player :cards cards)) (:players match) shuffled-cards)]
    (swap! matches #(update % id assoc :status :started :players players-with-cards))
    (get @matches id)))

(defn get-cards-for-player-name [matches id player-name]
  ;TODO Check that game has been started
  ;TODO We should create unique ids for players. Players should not know each others ids
  (let [match (get @matches id)]
    (when-not (= :started (:status match))
      (throw (Exception. "Match status should be started")))
    (:cards (first (filter #(= player-name (:name %)) (:players match))))))

(let [match {:id "1" :players [{:name "A"}
                               {:name "B"}
                               {:name "C"}
                               {:name "D"}]}
      matches (atom {"1" match})]
  (start matches "1")
  (get-cards-for-player-name matches "1" "C"))
