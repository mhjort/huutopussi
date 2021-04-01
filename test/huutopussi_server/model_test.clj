(ns huutopussi-server.model-test
  (:require [clojure.test :refer [deftest is]]
            [huutopussi-server.deck :as deck :refer [pick-card]]
            [huutopussi-server.model :as model]))

(def deck (deck/card-deck))
(def a-card (pick-card deck "A" :hearts))
(def b-card (pick-card deck "A" :clubs))
(def c-card (pick-card deck "A" :spades))
(def d-card (pick-card deck "A" :diamonds))
(def e-card (pick-card deck "K" :clubs))
(def f-card (pick-card deck "K" :hearts))
(def teams {"Team1" ["a" "c"] "Team2" ["b" "d"]})

(deftest after-init
  (let [game-model (model/init teams
                               "a"
                               [[a-card][b-card][c-card][d-card]])]
    (is (= {:current-round 0
            :next-player-id "a"
            :current-trick-cards []
            :game-ended? false
            :teams teams
            :events []
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [a-card] :possible-cards [a-card] :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [b-card] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [c-card] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [d-card] :possible-actions []}}}
           game-model))))

(deftest one-card-played
  (let [game-model (-> (model/init teams
                                   "a"
                                   [[a-card][b-card][c-card][d-card]])
                       (model/tick {:action-type :play-card
                                    :card a-card}))]
    (is (= {:current-round 0
            :next-player-id "b"
            :current-trick-cards [{:card a-card :player "a"}]
            :game-ended? false
            :teams teams
            :events [{:event-type :card-played :player "a" :value {:card a-card}}]
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [] :possible-cards [a-card] :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [b-card] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [c-card] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [d-card] :possible-actions []}}}
           game-model))))

(deftest one-round-played
  (let [game-model (-> (model/init teams
                                   "a"
                                   [[a-card e-card f-card][b-card][c-card][d-card]])
                       (model/tick {:action-type :play-card :card a-card})
                       (model/tick {:action-type :play-card :card b-card})
                       (model/tick {:action-type :play-card :card c-card})
                       (model/tick {:action-type :play-card :card d-card}))]
    (is (= {:current-round 1
            :next-player-id "a"
            :current-trick-cards []
            :game-ended? true
            :teams teams
            :events [{:event-type :card-played :player "a" :value {:card a-card}}
                     {:event-type :card-played :player "b" :value {:card b-card}}
                     {:event-type :card-played :player "c" :value {:card c-card}}
                     {:event-type :card-played :player "d" :value {:card d-card}}
                     {:event-type :round-won :player "a" :value {:card a-card :last-round? true}}]
            :players {"a" {:player-id "a"
                           :player-index 0
                           :hand-cards [e-card f-card]
                           :possible-cards [e-card f-card]
                           :possible-actions  [{:action-type "ask-for-trump" :target-player "c"}
                                               {:action-type "ask-for-half-trump" :suit :hearts :target-player "c"}
                                               {:action-type "ask-for-half-trump" :suit :clubs :target-player "c"}]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [] :possible-cards [b-card] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [] :possible-cards [c-card] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [] :possible-cards [d-card] :possible-actions []}}}
           game-model))))
