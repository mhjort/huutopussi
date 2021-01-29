(ns beacon-server.model-test
  (:require [clojure.test :refer [deftest is]]
            [beacon-server.deck :as deck :refer [pick-card]]
            [beacon-server.model :as model]))

(def deck (deck/card-deck))
(def a-card (pick-card deck "A" :hearts))
(def b-card (pick-card deck "A" :clubs))
(def c-card (pick-card deck "A" :spades))
(def d-card (pick-card deck "A" :diamonds))
(def e-card (pick-card deck "K" :clubs))
(def f-card (pick-card deck "K" :hearts))

(deftest after-init
  (let [game-model (model/init ["a" "b" "c" "d"]
                               [[a-card][b-card][c-card][d-card]])]
    (is (= {:current-round 0
            :next-player-id "a"
            :current-trick-cards []
            :game-ended? false
            :events []
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [a-card] :possible-cards [a-card]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [d-card]}}}
           game-model))))

(deftest one-card-played
  (let [game-model (-> (model/init ["a" "b" "c" "d"]
                                   [[a-card][b-card][c-card][d-card]])
                       (model/tick {:card a-card}))]
    (is (= {:current-round 0
            :next-player-id "b"
            :current-trick-cards [{:card a-card :player "a"}]
            :game-ended? false
            :events [{:event-type :card-played :player "a" :value a-card}]
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [] :possible-cards [a-card]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [d-card]}}}
           game-model))))

(deftest one-round-played
  (let [game-model (-> (model/init ["a" "b" "c" "d"]
                                   [[a-card e-card f-card][b-card][c-card][d-card]])
                       (model/tick {:card a-card})
                       (model/tick {:card b-card})
                       (model/tick {:card c-card})
                       (model/tick {:card d-card}))]
    (is (= {:current-round 1
            :next-player-id "a"
            :current-trick-cards []
            :game-ended? true
            :events [{:event-type :card-played :player "a" :value a-card}
                     {:event-type :card-played :player "b" :value b-card}
                     {:event-type :card-played :player "c" :value c-card}
                     {:event-type :card-played :player "d" :value d-card}
                     {:event-type :round-won :player "a" :value a-card}]
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [e-card f-card] :possible-cards [e-card f-card]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [] :possible-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [] :possible-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [] :possible-cards [d-card]}}}
           game-model))))
