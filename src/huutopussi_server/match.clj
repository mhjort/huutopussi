(ns huutopussi-server.match
  (:require [huutopussi-server.deck :as deck]
            [huutopussi-server.game :as game]
            [huutopussi-server.util :as util]
            [clojure.core.async :refer [chan go go-loop >! alts! timeout]]
            [clojure.tools.logging :as log]))

(defn- get-match [matches id]
  (let [match (get @matches id)]
    (when-not match
      (throw (Exception. (str "Could not find match with id " id " from " @matches))))
    match))

(defn get-match-status [matches id player-id events-since-str]
  (let [{:keys [teams status] :as match} (get-match matches id)]
    (when-not (= :started status)
      (throw (Exception. (str "Match " id " status should be started, but was " status))))
    (assoc (game/get-game-status match player-id events-since-str)
           :teams teams)))

(defn stop-match-loops [matches]
  (doseq [[id {:keys [match-loop-poison-pill]}] @matches]
    (when match-loop-poison-pill
      (log/info "Stopping match loop for game" id)
      (go (>! match-loop-poison-pill true)))))

(defn- map-vals [f m]
  (reduce-kv #(assoc %1 %2 (f %3)) {} m))

(defn mark-as-ready-to-start [matches id player]
  (let [mark-player-as-ready #(assoc-in % [:players player :ready-to-start?] true)
        updated-matches (swap! matches #(update % id mark-player-as-ready))]
    (when (every? :ready-to-start?
                  (vals (get-in updated-matches [id :players])))
      (go (>! (:all-player-ready (get-match matches id)) true))))
  (get-match matches id))

(defn start-match-loop [matches teams id players starting-players]
  (let [poison-pill (chan)]
    (go-loop [starting-players-in-order starting-players]
      (swap! matches #(update % id assoc :match-loop-poison-pill poison-pill))
      (let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
            [game-ended game-loop-poison-pill] (game/start teams
                                                           (first starting-players-in-order)
                                                           shuffled-cards
                                                           #(get-match matches id)
                                                           (fn [game-model]
                                                             (swap! matches #(update % id assoc :game-model game-model))))
            _ (swap! matches #(update % id assoc :status :started :players players))
            [_ ch] (alts! [game-ended poison-pill])]
        (if (= poison-pill ch)
          (do (>! game-loop-poison-pill true)
              (log/info "Match" id "ended"))
          (do
            (log/info "Game ended. Starting new one in 15 seconds")
            (let [[_ ch] (alts! [(timeout 15000) poison-pill])]
              (if (= poison-pill ch)
                (log/info "Match" id "ended")
                (recur (rest starting-players-in-order))))))))))

(defn start [matches id]
  (let [{:keys [players teams] :as match} (get-match matches id)
        flatted-teams (map-vals (fn [{:keys [players]}]
                                  players)
                                teams)
        starting-players (cycle (mapcat vector
                                        (first (vals flatted-teams))
                                        (second (vals flatted-teams))))
        _ (log/info "All players ready. Starting match" (util/pretty-print match))
        players-with-input-channels (map-vals #(assoc % :input-channel (chan)) players)]
    (start-match-loop matches flatted-teams id players-with-input-channels starting-players))
  @matches)

(defn run-action [matches id player-id action]
  (let [match (get @matches id)]
    (game/run-action match player-id action)))
