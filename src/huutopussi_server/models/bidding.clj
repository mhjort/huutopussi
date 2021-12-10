(ns huutopussi-server.models.bidding
  (:require [huutopussi-server.util :as util]
            [huutopussi-server.deck :as deck :refer [pick-card]]))

(def deck (deck/card-deck))
(def a-card (pick-card deck "A" :hearts))
(def b-card (pick-card deck "A" :clubs))
(def c-card (pick-card deck "A" :spades))
(def d-card (pick-card deck "A" :diamonds))
(def e-card (pick-card deck "K" :clubs))
(def f-card (pick-card deck "K" :hearts))

(def teams {:Team1 {:players ["a" "c"]}
            :Team2 {:players ["b" "d"]}})

(def initial-players {"a" {:player-id "a" :player-index 0 :hand-cards [a-card] :possible-cards [] :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [] :possible-actions []}})

(defn- set-target-score [{:keys [teams] :as game-model} player-id target-score]
 (let [player-team (get (util/teams-by-player teams) player-id)]
    (-> game-model
      (assoc-in [:teams player-team :target-score] target-score)
      (assoc :phase-ended? true))))


(set-target-score {:teams teams} "a" 50)

(defn tick [game-model {:keys [action-type player-id value]}]
  (case action-type
    :set-target-score (set-target-score game-model player-id value)))

(defn init [teams starting-player players]
  {:teams teams
   :players (assoc-in players [starting-player :possible-actions] [{:id "set-target-score"
                                                                    :action-type :set-target-score
                                                                    :possible-values (range 50 420 5)}])
   :next-player-id starting-player
   ;TODO Maybe this should not be set in bidding phase?
   :scores (reduce-kv (fn [m team _]
                                    (assoc m team 0))
                                  {}
                                  teams)
   ;TODO Maybe this should not be set in bidding phase?
   :current-round 0
   :events []
   :phase :bidding
   :phase-ended? false})

(init teams "a" initial-players)
