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

(defn choose-card [round-cards player-cards]
  (first (possible-cards round-cards player-cards nil)))

;TODO better impl for whole of this
(defn find-index [pred vec]
  (reduce-kv
    (fn [_ k v]
      (when (pred v)
        (reduced k)))
    nil
    vec))

(defn- cards-in-play-order [cards start-index]
  (let [[first-part second-part] (split-at start-index cards)]
      (concat second-part first-part)))

(defn- play-round [players start-player]
  (let [round-cards (loop [played-cards []
                           players-left (cards-in-play-order players start-player)]
                      (if (seq players-left)
                        (recur (conj played-cards (choose-card played-cards (first players-left)))
                               (rest players-left))
                        played-cards))
        win-card (winning-card round-cards nil)
        win-player (find-index #(some (partial = win-card) %) (vec players))]
    {:winning-card (winning-card round-cards nil)
     :winning-player win-player
     :trick-cards (map #(some (set %) round-cards) players)}))

;(let [deck (card-deck)
;      shuffled-cards (partition 9 (shuffle deck))]
;  (play-round shuffled-cards 1))

(defn- play-game [shuffled-cards]
  (loop [players (mapv (fn [cards] {:cards cards :tricks []})
                       shuffled-cards)
         start-player 0]
    (if (seq (:cards (first players)))
      (let [{:keys [winning-player trick-cards]} (play-round (map :cards players) start-player)
            updated-players (as-> players $
                              (update-in $ [winning-player :tricks] #(conj % trick-cards))
                              (mapv (fn [player played-card]
                                      (update player :cards #(remove (partial = played-card) %)))
                                    $ trick-cards))]
        (recur updated-players winning-player))
      players)))


;(let [deck (card-deck)
;      shuffled-cards (partition 9 (shuffle deck))]
;  (play-game shuffled-cards))

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
