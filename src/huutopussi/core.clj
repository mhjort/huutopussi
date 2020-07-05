(ns huutopussi.core)

(defn- card-deck []
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

(defn- print-card [{:keys [text suit]}]
  (str text (apply str (take 1 (name suit)))))

(let [deck (card-deck)
      [player1 player2 player3 player4] (partition 9 (shuffle deck))]
  (map print-card player1))

(defn- pick-card [text suit]
  (first (filter #(and (= text (:text %))
                       (= suit (:suit %))) (card-deck))))

(defn- winning-card [cards trump-suit]
  (let [round-suit (:suit (first cards))
        suit-matches #(= %1 (:suit %2))
        trump-suit-cards (if trump-suit
                           (filter (partial suit-matches trump-suit) cards)
                           [])
        potential-cards (if (seq trump-suit-cards)
                          trump-suit-cards
                          (filter (partial suit-matches round-suit) cards))]
    (first (sort-by :value #(compare %2 %1) potential-cards))))

(defn- possible-cards [round-cards player-cards]
  player-cards)

(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "9" :hearts)
             (pick-card "A" :hearts)]]
  (winning-card cards nil))

(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "9" :hearts)
             (pick-card "A" :clubs)]]
  (winning-card cards nil))

(let [cards [(pick-card "6" :hearts)
             (pick-card "8" :hearts)
             (pick-card "A" :clubs)
             (pick-card "6" :clubs)]]
  (winning-card cards :clubs))
