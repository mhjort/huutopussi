(ns huutopussi-server.game
  (:require [huutopussi-server.util :as util]
            [clojure.walk :as walk]
            [clojure.core.async :refer [chan go go-loop >! alts!]]
            [clojure.tools.logging :as log]))

(defn get-game-status [{:keys [status game-model id] :as match} player-id events-since-str]
  (let [events-since (if events-since-str
                       (Integer/parseInt events-since-str)
                       0)
        visible-game-model game-model]
    (when-not (= :started status)
      (throw (Exception. (str "Match " id " status should be started, but was " status))))
    (when-not game-model
      (throw (Exception. (str "Started match " id " does not have game model"))))
    {:current-round (:current-round visible-game-model)
     :current-trump-suit (:current-trump-suit visible-game-model)
     :events (drop events-since (:events visible-game-model))
     :next-player-name (:next-player-id visible-game-model)
     :possible-cards (get-in visible-game-model [:players player-id :possible-cards])
     :possible-actions (get-in visible-game-model [:players player-id :possible-actions])
     :hand-cards (get-in visible-game-model [:players player-id :hand-cards])
     :scores (:scores visible-game-model)
     :current-trick-cards (:current-trick-cards visible-game-model)}))

(defn- start-game-loop [model-tick get-match-game-model update-match-game-model!]
  (let [poison-pill (chan)
        game-ended (go-loop []
                            (let [{:keys [game-model players]} (get-match-game-model)
                                  next-player-id (:next-player-id game-model)
                                  input-channel (get-in players [next-player-id :input-channel])
                                  [action ch] (alts! [input-channel poison-pill])]
                              (when (= input-channel ch)
                                (log/info "Got input action" action)
                                (let [updated-game-model (model-tick game-model action)]
                                  (log/info "Action" action "run and model updated to" (util/pretty-print updated-game-model))
                                  (update-match-game-model! updated-game-model)
                                  (when-not (:game-ended? updated-game-model)
                                    (recur))))))]
    [game-ended poison-pill]))

(defn start [{:keys [teams
                     starting-player-id
                     cards-per-player
                     get-match-game-model
                     update-match-game-model!
                     model-init
                     model-tick]}]
  (let [game-model (model-init teams starting-player-id cards-per-player)]
    (update-match-game-model! game-model)
    (start-game-loop model-tick get-match-game-model update-match-game-model!)))

(defn run-action [{:keys [game-model id] :as match} player-id {:keys [action-type card-index] :as action}]
  (log/info "Going to run action with match" id "player-id" player-id "and action" action)
  (if (= (:next-player-id game-model) player-id)
    (let [player-details (get-in game-model [:players player-id])
          input-channel (get-in match [:players player-id :input-channel])
          matched-action (first (get (group-by :id (:possible-actions player-details)) (:id action)))
          ;TODO Play cards shourld not be special case
          executable-action (if (= "play-card" action-type)
                              {:action-type :play-card
                               :card (get-in player-details [:hand-cards card-index])}
                              (assoc matched-action :player-id player-id))]
      (if executable-action
        (go (>! input-channel executable-action))
        (do
          (log/warn "Could not find action" (:id action) "from match" id "for player" player-id)
          {:ok false}))
      {:ok true})
    {:ok false}))
