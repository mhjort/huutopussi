(ns huutopussi-server.models.bidding-test
  (:require [clojure.test :refer [deftest is]]
            [huutopussi-server.deck :as deck :refer [pick-card]]
            [huutopussi-server.models.bidding :as model]))

(def deck (deck/card-deck))
(def a-card (pick-card deck "A" :hearts))
(def b-card (pick-card deck "A" :clubs))
(def c-card (pick-card deck "A" :spades))
(def d-card (pick-card deck "A" :diamonds))
(def e-card (pick-card deck "K" :clubs))
(def f-card (pick-card deck "K" :hearts))
(def teams {:Team1 {:players ["a" "c"]}
            :Team2 {:players ["b" "d"]}})
(def initial-players {"a" {:player-id "a" :player-index 0 :hand-cards [a-card]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card]}})

(deftest after-init
  (let [game-model (model/init teams "a" initial-players)]
    (is (= {:current-round 0
            :next-player-id "a"
            :phase :bidding
            :phase-ended? false
            :teams teams
            :scores {:Team1 0 :Team2 0}
            :events []
            :players {"a" {:player-id "a"
                           :player-index 0
                           :hand-cards [a-card]
                           :possible-actions [{:id "set-target-score"
                                               :action-type :set-target-score
                                               :possible-values model/possible-target-scores}]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card]}}}
           game-model))))

(deftest setting-target-score
  (let [game-model (-> (model/init teams "a" initial-players)
                       (model/tick {:id "set-target-score"
                                    :action-type :set-target-score
                                    :player-id "a"
                                    :value 60}))]
    (is (= {:current-round 0
            :next-player-id "a"
            :phase :bidding
            :phase-ended? true
            :teams (assoc-in teams [:Team1 :target-score] 60)
            :scores {:Team1 0 :Team2 0}
            :events []
            :players {"a" {:player-id "a"
                           :player-index 0
                           :hand-cards [a-card]
                           :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card]}}}
           game-model))))
