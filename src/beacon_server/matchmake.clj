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

(defn get-match [matches id]
  (when-let [match (get @matches id)]
    (assoc match :id id)))

(defn find-match [matches player]
  (println "Finding match for" player)
  (let [max-players-in-match 4
        update-match (fn [new-player match]
                       (let [status (if (= max-players-in-match (inc (count (:players match))))
                                      :matched
                                      :waiting)]
                         (cond-> match
                           (nil? match) (assoc :declarer new-player)
                           true (assoc :status status)
                           true (update :players conj {:name new-player}))))
        [before after] (swap-vals! matches
                                   #(let [id (or (match-id-with-player-count-less-than % max-players-in-match)
                                                 (rand-str 6))]
                                      (update % id (partial update-match player))))
        [_ new-match _] (data/diff before after)]
    (get-match matches (first (keys new-match)))))

(find-match (atom {}) "a")
(find-match (atom {"1" {:declarer "a" :players ["a"]}}) "a")
