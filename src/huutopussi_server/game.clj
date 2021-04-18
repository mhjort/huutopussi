(ns huutopussi-server.game
  (:require [huutopussi-server.deck :as deck]
            [huutopussi-server.model :as model]
            [clojure.walk :as walk]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :refer [chan go go-loop <! >! alts! timeout]]
            [clojure.tools.logging :as log]))

(defn- pretty-print [m]
  (with-out-str (pprint m)))

(defn- get-match [matches id]
  (let [match (get @matches id)]
    (when-not match
      (throw (Exception. (str "Could not find match with id " id " from " @matches))))
    match))

(defn- player-name-by-id [match id]
  (get-in match [:players id :name]))

(defn- replace-player-ids-with-player-names [match game-model]
  ;This is a security feature. Internally model uses player ids that also act as api keys
  (walk/prewalk
    (fn [form]
      (cond
        (:player form) (update form :player (partial player-name-by-id match))
        (:next-player-id form) (update form :next-player-id (partial player-name-by-id match))
        (:target-player form) (update form :target-player (partial player-name-by-id match))
        :else form))
    game-model))

(defn get-game-status [matches id player-id events-since-str]
  (let [events-since (if events-since-str
                       (Integer/parseInt events-since-str)
                       0)
        {:keys [status game-model] :as match} (get-match matches id)
        scores (model/calculate-scores game-model)
        visible-game-model (replace-player-ids-with-player-names match game-model)]
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
     :scores scores
     :current-trick-cards (:current-trick-cards visible-game-model)}))

(defn- start-game-loop [matches id]
  (let [poison-pill (chan)
        game-ended (go-loop []
                            (let [{:keys [game-model players]} (get @matches id)
                                  next-player-id (:next-player-id game-model)
                                  input-channel (get-in players [next-player-id :input-channel])
                                  [action ch] (alts! [input-channel poison-pill])]
                              (when (= input-channel ch)
                                (log/info "Got input action" action)
                                (let [updated-game-model (model/tick game-model action)]
                                  (log/info "Action" action "run and model updated to" (pretty-print updated-game-model))
                                  (swap! matches #(update % id assoc :game-model updated-game-model))
                                  (when-not (:game-ended? updated-game-model)
                                    (recur))))))]
    [game-ended poison-pill]))

(defn stop-game-loops [matches]
  (doseq [[id {:keys [game-loop-poison-pill]}] @matches]
    (when game-loop-poison-pill
      (log/info "Stopping game loop for game" id)
      (go (>! game-loop-poison-pill true)))))

(defn- map-vals [f m]
  (reduce-kv #(assoc %1 %2 (f %3)) {} m))

(defn start-match-loop [matches teams id players starting-players]
  (let [poison-pill (chan)]
    (go-loop [starting-players-in-order starting-players]
             ;TODO Rename to match loop poison pill
             (swap! matches #(update % id assoc :game-loop-poison-pill poison-pill))
             (let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
                   game-model (model/init teams (first starting-players-in-order) shuffled-cards)
                   _ (swap! matches #(update % id assoc :status :started :game-model game-model :players players))
                   [game-ended game-loop-poison-pill] (start-game-loop matches id)
                   [_ ch] (alts! [game-ended poison-pill])]
               (if (= poison-pill ch)
                 (do (>! game-loop-poison-pill true)
                     (log/info "Match" id "ended"))
                 (do
                   (log/info "Game ended. Starting new one in 15 seconds")
                   ;TODO We should listen to poison-pill in here too
                   (<! (timeout 15000))
                   (recur (rest starting-players-in-order))))))))

(defn start [matches id]
  (let [{:keys [players teams] :as match} (get-match matches id)
        flatted-teams (map-vals (fn [{:keys [players]}]
                                  players)
                                teams)
        starting-players (cycle (mapcat vector
                                        (first (vals flatted-teams))
                                        (second (vals flatted-teams))))
        _ (log/info "All players ready. Starting match" (pretty-print match))
        players-with-input-channels (map-vals #(assoc % :input-channel (chan)) players)]
    (start-match-loop matches flatted-teams id players-with-input-channels starting-players))
  @matches)

(defn run-action [matches id player-id {:keys [action-type card-index] :as action}]
  (log/info "Going to run action with match" id "player-id" player-id "and action" action)
  (let [{:keys [game-model] :as match} (get @matches id)]
    (if (= (:next-player-id game-model) player-id)
      (let [player-details (get-in game-model [:players player-id])
            input-channel (get-in match [:players player-id :input-channel])
            ;TODO Play cards shourld not be special case
            executable-action (if (= "play-card" action-type)
                                {:action-type :play-card
                                 :card (get-in player-details [:hand-cards card-index])}
                                (first (get (group-by :id (:possible-actions player-details)) (:id action))))]
        (if executable-action
          (go (>! input-channel executable-action))
          (do
            (log/warn "Could not find action" (:id action) "from match" id "for player" player-id)
            {:ok false}))
        {:ok true})
      {:ok false})))
