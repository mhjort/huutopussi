(ns beacon-server.game
  (:require [beacon-server.deck :as deck]
            [beacon-server.model :as model]
            [clojure.core.async :refer [chan go go-loop <! >!]]
            [clojure.tools.logging :as log]))

(defn get-game-status [matches id player-name]
  ;TODO Check that game has been started
  ;TODO We should create unique ids for players. Players should not know each others ids
  (let [{:keys [game-model] :as match} (get @matches id)]
    (when-not (= :started (:status match))
      (throw (Exception. "Match status should be started")))
    {:current-round (:current-round game-model)
     :win-card (:current-round game-model)
     :next-player-name (:next-player-id game-model)
     :possible-cards (get-in game-model [:players player-name :possible-cards])
     :hand-cards (get-in game-model [:players player-name :hand-cards])
     :current-trick-cards (:current-trick-cards game-model)}))

(defn- start-game-loop [matches id]
  (go-loop []
           (let [match (get @matches id)
                 game-model (:game-model match)
                 next-player-id (:next-player-id game-model)
                 input-channel (:input-channel (first (filter #(= next-player-id (:name %)) (:players match))))
                 _ (log/info "Waiting for player" next-player-id "input from channel" input-channel)
                 card (<! input-channel)
                 _ (log/info "Got input card" card)
                 updated-game-model (model/tick game-model {:card card})]
             (log/info "Updated model" updated-game-model)
             (swap! matches #(update % id assoc :game-model updated-game-model))
             (when-not (:game-ended? updated-game-model)
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

(defn play-card [matches id player-id card-index]
  (log/info "Going to play card with match" id "player-id" player-id "and index" card-index)
  (let [{:keys [game-model] :as match} (get @matches id)]
    (if (= (:next-player-id game-model) player-id)
      (let [player-details (get-in game-model [:players player-id])
            card (get-in player-details [:hand-cards card-index])
            input-channel (:input-channel (first (filter #(= player-id (:name %)) (:players match))))]
        (log/info "Player" player-id "playing card" card "with input channel" input-channel)
        (go (>! input-channel card))
        {:ok true})
      {:ok false})))
