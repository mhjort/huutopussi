(ns beacon-server.deck
  (:require [clojure.string :as string]))

(def all-suits
  {:hearts {:trump-points 100}
   :diamonds {:trump-points 80}
   :clubs {:trump-points 60}
   :spades {:trump-points 40}})

(defn card-deck []
  (let [card-value-text-and-points (fn [i]
                              (condp = i
                                10 [14 "10" 10]
                                11 [11 "J" 2]
                                12 [12 "Q" 3]
                                13 [13 "K" 4]
                                14 [15 "A" 11]
                                [i (str i) 0]))
        cards (for [suit (keys all-suits)
                    value (range 6 15)]
                (let [[final-value text points] (card-value-text-and-points value)]
                  {:suit suit :value final-value :text text :points points}))]
    cards))

(defn shuffle-for-four-players [deck]
  (partition 9 (shuffle deck)))

(defn same-suit-for-four-players [deck]
  (partition 9 deck))

(defn print-card [{:keys [text suit]}]
  (str text (string/upper-case (apply str (take 1 (name suit))))))

(defn pick-card [deck text suit]
  (first (filter #(and (= text (:text %))
                       (= suit (:suit %))) deck)))
