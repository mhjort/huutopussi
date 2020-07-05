(ns huutopussi.core
  (:require [clojure.string :as string]))

(defn card-deck []
  (let [card-value-and-text (fn [i]
                              (condp = i
                                10 [14 "10"]
                                11 [11 "J"]
                                12 [12 "Q"]
                                13 [13 "K"]
                                14 [15 "A"]
                                [i (str i)]))
        cards (for [value (range 6 15)
                    suit [:hearts :clubs :spades :diamonds]]
                (let [[final-value text] (card-value-and-text value)]
                  {:suit suit :value final-value :text text}))]
    cards))

(defn print-card [{:keys [text suit]}]
  (str text (string/upper-case (apply str (take 1 (name suit))))))


(defn- pick-card [text suit]
  (first (filter #(and (= text (:text %))
                       (= suit (:suit %))) (card-deck))))

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

;(let [deck (card-deck)
;      [player1 player2 player3 player4] (partition 9 (shuffle deck))]
;  (map print-card player1))

(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "9" :hearts)]
      player-cards [(pick-card "J" :hearts)
                    (pick-card "Q" :hearts)]]
  ;(winning-card cards nil)
  (possible-cards cards player-cards nil))
(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "9" :hearts)
             (pick-card "A" :hearts)]
      player-cards [(pick-card "J" :clubs)
                    (pick-card "Q" :clubs)]]
  (winning-card cards nil)
  (possible-cards cards player-cards nil))

(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "9" :hearts)
             (pick-card "A" :clubs)]]
  (winning-card cards nil))

(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "A" :clubs)
             (pick-card "6" :clubs)]
      player-cards [(pick-card "6" :clubs)
                    (pick-card "J" :clubs)
                    (pick-card "Q" :diamonds)]]
  (winning-card cards :clubs)
  (possible-cards cards player-cards :clubs))
