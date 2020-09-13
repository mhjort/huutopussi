(ns beacon-server.game
  (:require [beacon-server.model.deck :as deck]
            [clojure.core.async :refer [chan go go-loop <! <!! >!]]
            [clojure.tools.logging :as log]
            ))

(defn get-cards-for-player-name [matches id player-name]
  ;TODO Check that game has been started
  ;TODO We should create unique ids for players. Players should not know each others ids
  (let [match (get @matches id)]
    (when-not (= :started (:status match))
      (throw (Exception. "Match status should be started")))
    (:hand-cards (first (filter #(= player-name (:player-id %)) (-> match :game-model :players))))))

(defn winning-card [cards trump-suit]
  (let [round-suit (:suit (first cards))
        suit-matches #(= %1 (:suit %2))
        trump-suit-cards (if trump-suit
                           (filter (partial suit-matches trump-suit) cards)
                           [])
        potential-cards (if (seq trump-suit-cards)
                          trump-suit-cards
                          (filter (partial suit-matches round-suit) cards))]
    (first (sort-by :value #(compare %2 %1) potential-cards))))

(defn possible-cards [round-cards player-cards trump-suit]
  (let [round-suit (:suit (first round-cards))
        winning-card (winning-card round-cards trump-suit)
        trump-card-played? (= trump-suit (:suit winning-card))
        suit-matches #(= %1 (:suit %2))
        same-suit-cards (filter (partial suit-matches round-suit) player-cards)
        higher-same-suit-cards (filter #(> (:value %) (:value winning-card)) same-suit-cards)
        trump-suit-cards (if trump-suit
                           (filter (partial suit-matches trump-suit) player-cards)
                           [])
        higher-trump-suit-cards (filter #(> (:value %) (:value winning-card)) trump-suit-cards)]
    (cond
      (seq same-suit-cards) (if (or trump-card-played? (empty? higher-same-suit-cards))
                              same-suit-cards
                              higher-same-suit-cards)
      (seq trump-suit-cards) (if (and trump-card-played? (seq higher-trump-suit-cards))
                               higher-trump-suit-cards
                               trump-suit-cards)
      :else player-cards)))

(defn- play-card [{:keys [next-player] :as game-model} {:keys [card]}]
  (let [updated-game-model (-> game-model
                               (update :current-trick-cards conj {:player next-player :card card})
                               (update-in [:players next-player :hand-cards] (fn [hand-cards]
                                                                               (remove #(= card %) hand-cards))))
        possible-cards-for-next-player (fn [next-player]
                                         (possible-cards (map :card (:current-trick-cards updated-game-model))
                                                         (get-in updated-game-model [:players next-player :hand-cards])
                                                         nil))
        trick-ended? (= 4 (count (:current-trick-cards updated-game-model)))]
    (if trick-ended?
      (let [win-card (winning-card (map :card (:current-trick-cards updated-game-model)) nil)
            win-player (-> (filter #(= win-card (:card %)) (:current-trick-cards updated-game-model))
                           first
                           :player)]
        (-> updated-game-model
            (update :current-round inc)
            (assoc :current-trick-cards [])
            (assoc :win-card win-card)
            (assoc :next-player win-player)
            (assoc-in [:player win-player :possible-cards] (possible-cards-for-next-player win-player))))
      (let [next-player (inc (:next-player updated-game-model))] ;TODO This works only in first round
        (-> updated-game-model
            (assoc :next-player next-player)
            (assoc-in [:player next-player :possible-cards] (possible-cards-for-next-player next-player)))))))

(defn- init-game [player-ids shuffled-cards cards-per-player]
  (let [game-model {:current-round 0
                    :next-player 0
                    :current-trick-cards []
                    :players (mapv (fn [player-id cards]
                                     (let [hand-cards (vec (take cards-per-player cards))]
                                       {:player-id player-id
                                        :hand-cards hand-cards
                                        :possible-cards hand-cards}))
                                   player-ids
                                   shuffled-cards)}]
    game-model))

(defn- start-game-loop [matches id]
  (go-loop []
           (let [match (get @matches id)
                 game-model (:game-model match)
                 next-player (:next-player game-model)
                 input-channel (get-in match [:players next-player :input-channel])
                 _ (log/info "Waiting for player" next-player "input from channel" input-channel)
                 card (<! input-channel)
                 _ (log/info "Got input card" card)
                 updated-game-model (play-card game-model {:card card})]
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
        game-model (init-game (mapv :name (:players match)) shuffled-cards 9)]
    (swap! matches #(update % id assoc :status :started :game-model game-model :players players-with-input-channels))
    (start-game-loop matches id)
    (get @matches id)))

(defn enter-card [matches id player card-index]
  (let [match (get @matches id)
        player-details (first (filter #(= player (:player-id %)) (-> match :game-model :players)))
        card (get-in player-details [:hand-cards card-index])
        input-channel (:input-channel (first (filter #(= player (:name %)) (:players match))))]
    (log/info "Player" player "playing card" card "with input channel" input-channel)
    (go (>! input-channel card))
    {:ok true}))

(enter-card (atom {"1" {:players [{:name "a" :input-channel (chan)}]
                         :game-model {:players [{:player-id "a" :hand-cards ["A" "K"]}]}}}) "1" "a" (Integer/parseInt "1"))

;(let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
;      game-model (init-game ["a" "b" "c" "d"] shuffled-cards 2)
;      do-it (fn [{:keys [next-player players] :as game-model}]
;              (let [card-to-play (-> (nth players next-player) :possible-cards first)]
;                    (play-card game-model {:card card-to-play})))]
;  (-> game-model
;     (do-it)
;     (do-it)
;     (do-it)))
     ;(do-it)))
   ;  (do-it)))

