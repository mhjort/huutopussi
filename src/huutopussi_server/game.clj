(ns huutopussi-server.game
  (:require [huutopussi-server.util :as util]
            [clojure.core.async :refer [chan go go-loop >! alts!]]
            [clojure.tools.logging :as log]))

(defn team-scores [{:keys [scores] :as game-model}]
  (reduce-kv (fn [m team score]
               (let [target-score (get-in game-model [:teams team :target-score])]
                 (cond-> m
                   true (assoc-in [team :current] score)
                   target-score (assoc-in [team :target] target-score))))
             {}
             scores))

(defn get-game-status [{:keys [status game-model id events]} player-id]
  (when-not (= :started status)
    (throw (Exception. (str "Match " id " status should be started, but was " status))))
  (when-not game-model
    (throw (Exception. (str "Started match " id " does not have game model"))))
  {:current-round (:current-round game-model)
   :current-trump-suit (:current-trump-suit game-model)
   :events events
   :phase (:phase game-model)
   :next-player-name (:next-player-id game-model)
   :possible-cards (get-in game-model [:players player-id :possible-cards])
   :possible-actions (get-in game-model [:players player-id :possible-actions])
   :hand-cards (get-in game-model [:players player-id :hand-cards])
   :scores (team-scores game-model)
   :current-trick-cards (:current-trick-cards game-model)})


(defn- init-model [model-fns {:keys [teams next-player-id players] :as previous-game-model}]
  (if-let [first-model-init (:model-init (first model-fns))]
    (first-model-init teams next-player-id players)
    previous-game-model))

(defn- start-game-loop [model-fns get-match-game-model update-match-game-model!]
  (let [poison-pill (chan)
        game-ended (go-loop [active-model-fns model-fns]
                     (let [model-tick (:model-tick (first active-model-fns))
                           {:keys [game-model players]} (get-match-game-model)
                           next-player-id (:next-player-id game-model)
                           input-channel (get-in players [next-player-id :input-channel])
                           [action ch] (alts! [input-channel poison-pill])]
                       (when (= input-channel ch)
                         (log/info "Got input action" action)
                         (let [{:keys [phase-ended?] :as updated-game-model} (model-tick game-model action)
                               [model-for-next-round
                                model-fns-for-next-round] (if phase-ended?
                                                            (do
                                                              (update-match-game-model! updated-game-model)
                                                              [(init-model (rest active-model-fns) updated-game-model)
                                                               (rest active-model-fns)])
                                                            [updated-game-model active-model-fns])]
                           (log/info "Action" action "run and model updated to" (util/pretty-print model-for-next-round))
                           (update-match-game-model! model-for-next-round)
                           (when (seq model-fns-for-next-round)
                             (recur model-fns-for-next-round))))))]
    [game-ended poison-pill]))

(defn start [{:keys [teams
                     starting-player-id
                     cards-per-player
                     get-match-game-model
                     update-match-game-model!
                     model-fns]}]
  (let [game-model (init-model model-fns {:teams teams
                                          :next-player-id starting-player-id
                                          :players (util/generate-players teams cards-per-player)})]
    (update-match-game-model! game-model)
    (start-game-loop model-fns get-match-game-model update-match-game-model!)))

(defn run-action [{:keys [game-model] :as match} player-id {:keys [action-type card-index value] :as action}]
  (log/info "Going to run action with player-id" player-id "and action" action "from match" (keys match))
  (if (= (:next-player-id game-model) player-id)
    (let [player-details (get-in game-model [:players player-id])
          input-channel (get-in match [:players player-id :input-channel])
          matched-action (first (get (group-by :id (:possible-actions player-details)) (:id action)))
          ;TODO Play cards should not be special case
          executable-action (if (= "play-card" action-type)
                              {:action-type :play-card
                               :card (get-in player-details [:hand-cards card-index])}
                              ;TODO We should check if value was one of the possible-values
                              (cond-> matched-action
                                true (assoc :player-id player-id)
                                value (assoc :value value)))]
      (log/info "Matched action for player-id" player-id "was" action)
      (if executable-action
        (go (>! input-channel executable-action))
        (do
          (log/warn "Could not find action" (:id action) "for player" player-id)
          {:ok false}))
      {:ok true})
    {:ok false}))
