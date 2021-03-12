(ns beacon-server.game
  (:require [beacon-server.deck :as deck]
            [beacon-server.model :as model]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :refer [chan go go-loop >! alts!]]
            [clojure.tools.logging :as log]))

(defn- pretty-print [m]
  (with-out-str (pprint m)))

(defn- get-match [matches id]
  (let [match (get @matches id)]
    (when-not match
      (throw (Exception. (str "Could not find match with id " id " from " @matches))))
    match))

(defn get-game-status [matches id player-id events-since-str]
  (let [events-since (if events-since-str
                       (Integer/parseInt events-since-str)
                       0)
        {:keys [status game-model] :as match} (get-match matches id)
        player-name-by-id #(get-in match [:players % :name])]
    (when-not (= :started status)
      (throw (Exception. (str "Match " id " status should be started, but was " status))))
    {:current-round (:current-round game-model)
     :events (map #(update % :player player-name-by-id) (drop events-since (:events game-model)))
     :next-player-name (player-name-by-id (:next-player-id game-model))
     :possible-cards (get-in game-model [:players player-id :possible-cards])
     :hand-cards (get-in game-model [:players player-id :hand-cards])
     :scores (model/calculate-scores game-model)
     :current-trick-cards (:current-trick-cards game-model)}))

(defn- start-game-loop [matches id]
  (let [poison-pill (chan)]
    (go-loop []
             (let [{:keys [game-model players]} (get @matches id)
                   next-player-id (:next-player-id game-model)
                   input-channel (get-in players [next-player-id :input-channel])
                   [card ch] (alts! [input-channel poison-pill])]
               (when (= input-channel ch)
                 (log/info "Got input card" card)
                 (let [updated-game-model (model/tick game-model {:card card})]
                   (log/info "Card" card "played and model updated to" (pretty-print updated-game-model))
                   (swap! matches #(update % id assoc :game-model updated-game-model))
                   (when-not (:game-ended? updated-game-model)
                     (recur))))))
    poison-pill))

(defn stop-game-loops [matches]
  (doseq [[id {:keys [game-loop-poison-pill]}] @matches]
    (when game-loop-poison-pill
      (log/info "Stopping game loop for game" id)
      (go (>! game-loop-poison-pill true)))))

(defn- map-kv [f m]
  (reduce-kv #(assoc %1 %2 (f %3)) {} m))

(defn start [matches id]
  (let [{:keys [players teams] :as match} (get-match matches id)
        _ (log/info "All players ready. Starting match" (pretty-print match))
        shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
        players-with-input-channels (map-kv #(assoc % :input-channel (chan)) players)
        game-model (model/init teams shuffled-cards)
        _ (swap! matches #(update % id assoc :status :started :game-model game-model :players players-with-input-channels))
        game-loop-poison-pill (start-game-loop matches id)]
    (swap! matches #(update % id assoc :game-loop-poison-pill game-loop-poison-pill))))

(defn run-action [matches id player-id {:keys [action-type card-index] :as action}]
  (case action-type
    "play-card" (do
                  (log/info "Going to run action with match" id "player-id" player-id "and action" action)
                  (let [{:keys [game-model] :as match} (get @matches id)]
                    (if (= (:next-player-id game-model) player-id)
                      (let [player-details (get-in game-model [:players player-id])
                            card (get-in player-details [:hand-cards card-index])
                            input-channel (get-in match [:players player-id :input-channel])]
                        (go (>! input-channel card))
                        {:ok true})
                      {:ok false})))))
