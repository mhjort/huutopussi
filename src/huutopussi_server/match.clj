(ns huutopussi-server.match
  (:require [huutopussi-server.deck :as deck]
            [huutopussi-server.game :as game]
            [huutopussi-server.models.marjapussi :as marjapussi]
            [huutopussi-server.util :as util]
            [huutopussi-server.schemas :as schemas]
            [clojure.core.async :refer [chan go go-loop >! alts! timeout]]
            [clojure.tools.logging :as log]))

(def initial-model-fns
  [{:model-init marjapussi/init
    :model-tick marjapussi/tick}])

(defn- get-match [matches id]
  (let [match (get @matches id)]
    (when-not match
      (throw (Exception. (str "Could not find match with id " id " from " @matches))))
    match))

(defn get-match-status [matches id player-id events-since-str]
  (let [{:keys [teams status] :as match} (get-match matches id)]
    (when-not (= :started status)
      (throw (Exception. (str "Match " id " status should be started, but was " status))))
    (util/replace-player-ids-with-player-names match
                                               (assoc (game/get-game-status match player-id events-since-str)
                                                      :teams teams))))

(defn stop-match-loops [matches]
  (doseq [[id {:keys [match-loop-poison-pill]}] @matches]
    (when match-loop-poison-pill
      (log/info "Stopping match loop for game" id)
      (go (>! match-loop-poison-pill true)))))


(defn mark-as-ready-to-start [matches id player]
  (let [mark-player-as-ready #(assoc-in % [:players player :ready-to-start?] true)
        updated-matches (swap! matches #(update % id mark-player-as-ready))]
    (when (every? :ready-to-start?
                  (vals (get-in updated-matches [id :players])))
      (go (>! (:all-player-ready (get-match matches id)) true))))
  (get-match matches id))

(defn- update-total-score [matches id]
  (let [{:keys [game-model teams]} (get-match matches id)
        scores (:scores game-model)
        updated-teams (reduce-kv (fn [m team team-stats]
                                   (let [score-for-team (team scores)]
                                     (assoc m team (update team-stats :total-score + score-for-team))))
                                 {} teams)]
    (swap! matches #(update % id assoc :teams updated-teams))))

(defn- start-match-loop [matches
                         teams
                         id
                         players
                         starting-players
                         {:keys [time-before-starting-next-round]
                          :or {time-before-starting-next-round 15000}}
                         model-fns]
  (let [poison-pill (chan)
        update-game-model! (fn [game-model]
                                  (swap! matches #(update % id assoc :game-model game-model)))]
    (go-loop [starting-players-in-order starting-players]
      (swap! matches #(update % id assoc :match-loop-poison-pill poison-pill))
      (let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
            [game-ended game-loop-poison-pill] (game/start {:teams teams
                                                            :starting-player-id (first starting-players-in-order)
                                                            :cards-per-player shuffled-cards
                                                            :get-match-game-model #(get-match matches id)
                                                            :update-match-game-model! update-game-model!
                                                            :model-fns model-fns})
            _ (swap! matches #(update % id assoc :status :started :players players))
            [_ ch] (alts! [game-ended poison-pill])]
        (if (= poison-pill ch)
          (do (>! game-loop-poison-pill true)
              (log/info "Match" id "ended"))
          (do
            (update-total-score matches id)
            (log/info "Game ended. Starting new one in" time-before-starting-next-round "milliseconds")
            (let [[_ ch] (alts! [(timeout time-before-starting-next-round) poison-pill])]
              (if (= poison-pill ch)
                (log/info "Match" id "ended")
                (recur (rest starting-players-in-order))))))))))

(defn start [matches id model-fns options]
  (let [{:keys [players teams] :as match} (get-match matches id)
        _ (schemas/validate-schema schemas/Teams teams)
        flatted-teams (util/map-vals (fn [{:keys [players]}]
                                       players)
                                     teams)
        starting-players (cycle (mapcat vector
                                        (first (vals flatted-teams))
                                        (second (vals flatted-teams))))
        _ (log/info "All players ready. Starting match" (util/pretty-print match))
        players-with-input-channels (util/map-vals #(assoc % :input-channel (chan)) players)]
    (start-match-loop matches teams id players-with-input-channels starting-players options model-fns))
  @matches)

(defn run-action [matches id player-id action]
  (let [match (get @matches id)]
    (game/run-action match player-id action)))
