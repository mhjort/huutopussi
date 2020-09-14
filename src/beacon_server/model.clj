(ns beacon-server.model
  (:require [beacon-server.deck :as deck]))

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

(defn- next-player-index [current-index number-of-players]
  (let [possible-index (inc current-index)]
    (if (= number-of-players possible-index)
      0
      possible-index)))

(defn tick [{:keys [next-player number-of-players] :as game-model} {:keys [card]}]
  (let [updated-game-model (-> game-model
                               (update :current-trick-cards conj {:player next-player :card card})
                               (update-in [:players next-player :hand-cards] (fn [hand-cards]
                                                                               (vec (remove #(= card %) hand-cards)))))
        possible-cards-for-next-player (fn [next-player]
                                         (possible-cards (mapv :card (:current-trick-cards updated-game-model))
                                                         (get-in updated-game-model [:players next-player :hand-cards])
                                                         nil))
        trick-ended? (= number-of-players (count (:current-trick-cards updated-game-model)))
        ;TODO Better check for this
        game-ended? (and trick-ended?
                         (empty? (get-in updated-game-model [:players next-player :hand-cards])))]
    (if trick-ended?
      (let [win-card (winning-card (map :card (:current-trick-cards updated-game-model)) nil)
            win-player (-> (filter #(= win-card (:card %)) (:current-trick-cards updated-game-model))
                           first
                           :player)]
        (-> updated-game-model
            (update :current-round inc)
            (assoc :game-ended? game-ended?)
            (assoc :current-trick-cards [])
            (assoc :win-card win-card)
            (assoc :next-player win-player)
            (assoc-in [:players win-player :possible-cards] (possible-cards-for-next-player win-player))))
      (let [next-player (next-player-index (:next-player updated-game-model) number-of-players)]
        (-> updated-game-model
            (assoc :game-ended? game-ended?)
            (assoc :next-player next-player)
            (assoc-in [:players next-player :possible-cards] (possible-cards-for-next-player next-player)))))))

(defn init [player-ids shuffled-cards cards-per-player]
  (let [game-model {:current-round 0
                    :next-player 0
                    :number-of-players (count player-ids)
                    :current-trick-cards []
                    :game-ended? false
                    :players (mapv (fn [player-id cards]
                                     (let [hand-cards (vec (take cards-per-player cards))]
                                       {:player-id player-id
                                        :hand-cards hand-cards
                                        :possible-cards hand-cards}))
                                   player-ids
                                   shuffled-cards)}]
    game-model))

(let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
      game-model (init ["a" "b" "c" "d"] shuffled-cards 2)
      do-it (fn [{:keys [next-player players] :as game-model}]
              (let [card-to-play (-> (nth players next-player) :possible-cards first)]
                    (tick game-model {:card card-to-play})))
      end-result  (-> game-model
     (do-it)
     (do-it)
     (do-it)
     (do-it)
     (do-it)
     (do-it)
;     (do-it)
     (do-it))]
  [(:next-player end-result) (:win-card end-result) (:game-ended? end-result) (map :hand-cards (:players end-result))])
