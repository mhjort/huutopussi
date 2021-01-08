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

(defn- select-next-player-id [current-index players]
  (let [possible-index (inc current-index)
        next-player-index (if (= (count players) possible-index)
                            0
                            possible-index)
        next-player (first (filter #(= next-player-index (:player-index %)) (vals players)))]
    (:player-id next-player)))

(defn tick [{:keys [next-player-id players] :as game-model} {:keys [card]}]
  (let [updated-game-model (-> game-model
                               (update :current-trick-cards conj {:player next-player-id :card card})
                               (update :events conj {:event-type :card-played :player next-player-id :value card})
                               (update-in [:players next-player-id :hand-cards] (fn [hand-cards]
                                                                                     (vec (remove #(= card %) hand-cards)))))
        possible-cards-for-next-player (fn [next-player]
                                         (possible-cards (mapv :card (:current-trick-cards updated-game-model))
                                                         (get-in updated-game-model [:players next-player :hand-cards])
                                                         nil))
        trick-ended? (= (count players) (count (:current-trick-cards updated-game-model)))
        ;TODO Better check for this
        game-ended? (and trick-ended?
                         (empty? (get-in updated-game-model [:players next-player-id :hand-cards])))]
    (if trick-ended?
      (let [win-card (winning-card (map :card (:current-trick-cards updated-game-model)) nil)
            win-player (-> (filter #(= win-card (:card %)) (:current-trick-cards updated-game-model))
                           first
                           :player)]
        (-> updated-game-model
            (update :current-round inc)
            (assoc :game-ended? game-ended?)
            (assoc :current-trick-cards [])
            (update :events conj {:event-type :round-won :player win-player :value win-card})
            (assoc :next-player-id win-player)
            (assoc-in [:players win-player :possible-cards] (possible-cards-for-next-player win-player))))
      (let [next-player-id (select-next-player-id (get-in updated-game-model [:players next-player-id :player-index])
                                                  (:players updated-game-model))]
        (-> updated-game-model
            (assoc :game-ended? game-ended?)
            (assoc :next-player-id next-player-id)
            (assoc-in [:players next-player-id :possible-cards] (possible-cards-for-next-player next-player-id)))))))

(defn init [player-ids shuffled-cards]
  (let [game-model {:current-round 0
                    :next-player-id (first player-ids)
                    :current-trick-cards []
                    :events []
                    :game-ended? false
                    :players (into {} (map (fn [[player-index player-id] cards]
                                             (let [hand-cards (vec cards)]
                                               [player-id {:player-id player-id
                                                           :player-index player-index
                                                           :hand-cards hand-cards
                                                           :possible-cards hand-cards}]))
                                           (map-indexed vector player-ids)
                                           shuffled-cards))}]
    game-model))

(let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
      game-model (init ["a" "b" "c" "d"] (map #(take 2 %) shuffled-cards))
      do-it (fn [{:keys [next-player-id players] :as game-model}]
              (let [card-to-play (first (get-in players [next-player-id :possible-cards]))]
                    (println "Player" next-player-id "plays" card-to-play)
                    (tick game-model {:card card-to-play})))
      end-result  (-> game-model
     (do-it)
     (do-it)
     (do-it)
     (do-it)
  ;   (do-it)
     (do-it)
     (do-it)
     (do-it))]
  [(:next-player-id end-result) (:win-card end-result) (:game-ended? end-result) (map :hand-cards (vals (:players end-result)))])
