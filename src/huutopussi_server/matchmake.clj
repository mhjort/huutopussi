(ns huutopussi-server.matchmake
  (:require [clojure.data :as data]
            [huutopussi-server.match :as match]
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
    (let [{:keys [status declarer players teams]} match]
      ;TODO Use separate atom for matchmake and actual game. This is a mess!
      {:id id
       :status status
       :declarer declarer
       :teams (reduce-kv (fn [m team-name {player-ids :players}]
                           (assoc m (name team-name) (map #(:name (get players %)) player-ids)))
                         {}
                         teams)
       :players (mapv #(select-keys % [:name]) (vals players))})
    (throw (Exception. (str "No such match: " id)))))

(defn- form-teams [players]
  {:Team1 {:players (map :id (take 2 (vals players)))
            :total-score 0}
   :Team2 {:players (map :id (drop 2 (vals players)))
            :total-score 0}})

(defn find-match [matches player]
  (log/info "Finding match for" player)
  (let [max-players-in-match 4
        player-id (rand-str 6)
        update-match (fn [new-player {:keys [players] :as match}]
                       (let [status (if (= max-players-in-match (inc (count players)))
                                      :matched
                                      :waiting)
                             updated-players (assoc players (:id new-player) new-player)]
                         (cond-> match
                           (nil? match) (assoc :declarer (:name new-player) :players {})
                           true (assoc :status status)
                           (= :matched status) (assoc :teams (form-teams updated-players))
                           true (assoc :players updated-players))))
        [before after] (swap-vals! matches
                                   #(let [match-id (or (match-id-with-player-count-less-than % max-players-in-match)
                                                       (rand-str 6))]
                                      (update % match-id (partial update-match {:name player :id player-id}))))
        [_ new-match _] (data/diff before after)]
    (assoc (get-match matches (first (keys new-match))) :player-id player-id)))

(defn mark-as-ready-to-start [matches id player]
  (let [mark-player-as-ready #(assoc-in % [:players player :ready-to-start?] true)
        updated-matches (swap! matches #(update % id mark-player-as-ready))]
    (when (every? :ready-to-start?
                  (vals (get-in updated-matches [id :players])))
      ;TODO First model should come somewhere else and should not
      (match/start matches id match/initial-model-fns {})))
  (get-match matches id))

;(find-match (atom {}) "a")
;(find-match (atom {"1" {:declarer "a" :players {"a" {:name "a" :id "GASJ"}
;                                                "b" {:name "b" :id "DSD"}
;                                                "c" {:name "c" :id "DASDSA"}
;                                                }}}) "d")
