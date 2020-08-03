(ns beacon-server.matchmake
  (:require [clojure.data :as data]))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn- match-id-with-player-count-less-than [matches max-player-count]
  (let [player-counts (map (fn [[k v]]
                             [k (count (:players v))])
                           matches)
        match-id-and-player-count (first (filter (fn [[_ player-count]]
                                                   (< player-count max-player-count)) player-counts))]
    (first match-id-and-player-count)))

(defn find-match [matches player]
  (println "Finding match for" player)
  (let [[before after] (swap-vals! matches
                                   #(let [id (or (match-id-with-player-count-less-than % 4)
                                                 (rand-str 6))]
                                      (update-in % [id :players] conj player)))
        [_ new-match _] (data/diff before after)]
    new-match))
