(ns beacon-server.game
  (:require [beacon-server.deck :as deck]
            [beacon-server.model :as model]
            [clojure.core.async :refer [chan go go-loop <! >!]]
            [clojure.tools.logging :as log]))

(defn get-cards-for-player-name [matches id player-name]
  ;TODO Check that game has been started
  ;TODO We should create unique ids for players. Players should not know each others ids
  (let [match (get @matches id)]
    (when-not (= :started (:status match))
      (throw (Exception. "Match status should be started")))
    {:hand-cards (:hand-cards (first (filter #(= player-name (:player-id %)) (-> match :game-model :players))))
     :current-trick-cards (-> match :game-model :current-trick-cards)}))

(defn- start-game-loop [matches id]
  (go-loop []
           (let [match (get @matches id)
                 game-model (:game-model match)
                 next-player (:next-player game-model)
                 input-channel (get-in match [:players next-player :input-channel])
                 _ (log/info "Waiting for player" next-player "input from channel" input-channel)
                 card (<! input-channel)
                 _ (log/info "Got input card" card)
                 updated-game-model (model/tick game-model {:card card})]
             (log/info "Updated model" updated-game-model)
             (swap! matches #(update % id assoc :game-model updated-game-model))
             (when-not (:win-card updated-game-model)
               (recur)))))

(defn start [matches id]
  (let [match (get @matches id)
        shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
        players-with-input-channels (mapv (fn [player]
                                            {:name (:name player) :input-channel (chan)})
                                          (:players match))
        game-model (model/init (mapv :name (:players match)) shuffled-cards 9)]
    (swap! matches #(update % id assoc :status :started :game-model game-model :players players-with-input-channels))
    (start-game-loop matches id)
    (get @matches id)))

(defn play-card [matches id player card-index]
  (let [match (get @matches id)
        player-details (first (filter #(= player (:player-id %)) (-> match :game-model :players)))
        card (get-in player-details [:hand-cards card-index])
        input-channel (:input-channel (first (filter #(= player (:name %)) (:players match))))]
    (log/info "Player" player "playing card" card "with input channel" input-channel)
    (go (>! input-channel card))
    {:ok true}))

;(play-card (atom {"1" {:players [{:name "a" :input-channel (chan)}]
;                         :game-model {:players [{:player-id "a" :hand-cards ["A" "K"]}]}}}) "1" "a" (Integer/parseInt "1"))
