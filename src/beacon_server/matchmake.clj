(ns beacon-server.matchmake
  (:require [clojure.data :as data]
            [clojure.tools.logging :as log]))

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
  (if-let [match (get @matches id)]
    (let [{:keys [status declarer players]} match]
      ;TODO Use separate atom for matchmake and actual game. This is a mess!
      {:id id
       :status status
       :declarer declarer
       :players (mapv #(select-keys % [:name]) (vals players))})
    (throw (Exception. (str "No such match: " id)))))

(defn find-match [matches player]
  (log/info "Finding match for" player)
  (let [max-players-in-match 4
        player-id (rand-str 6)
        update-match (fn [new-player match]
                       (let [status (if (= max-players-in-match (inc (count (:players match))))
                                      :matched
                                      :waiting)]
                         (cond-> match
                           (nil? match) (assoc :declarer (:name new-player) :players {})
                           true (assoc :status status)
                           true (update :players assoc (:id new-player) new-player))))
        [before after] (swap-vals! matches
                                   #(let [match-id (or (match-id-with-player-count-less-than % max-players-in-match)
                                                       (rand-str 6))]
                                      (update % match-id (partial update-match {:name player :id player-id}))))
        [_ new-match _] (data/diff before after)]
    (assoc (get-match matches (first (keys new-match))) :player-id player-id)))

;(find-match (atom {}) "a")
;(find-match (atom {"1" {:declarer "a" :players {"a" {:name "a"}}}}) "b")
