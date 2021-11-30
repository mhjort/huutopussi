(ns huutopussi-server.model-test
  (:require [clojure.test :refer [deftest is]]
            [huutopussi-server.util :as util]
            [huutopussi-server.deck :as deck :refer [pick-card]]
            [huutopussi-server.model :as model]))

(def deck (deck/card-deck))
(def a-card (pick-card deck "A" :hearts))
(def b-card (pick-card deck "A" :clubs))
(def c-card (pick-card deck "A" :spades))
(def d-card (pick-card deck "A" :diamonds))
(def e-card (pick-card deck "K" :clubs))
(def f-card (pick-card deck "K" :hearts))
(def teams {:Team1 ["a" "c"] :Team2 ["b" "d"]})
(def initial-players {"a" {:player-id "a" :player-index 0 :hand-cards [a-card] :possible-cards [] :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [] :possible-actions []}})

(deftest after-init
  (let [game-model (model/init teams "a" initial-players)]
    (is (= {:current-round 0
            :next-player-id "a"
            :current-trick-cards []
            :phase-ended? false
            :teams teams
            :scores {:Team1 0 :Team2 0}
            :events []
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [a-card] :possible-cards [a-card] :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [] :possible-actions []}}}
           game-model))))

(deftest one-card-played
  (let [game-model (-> (model/init teams
                                   "a"
                                   initial-players)
                       (model/tick {:action-type :play-card
                                    :card a-card}))]
    (is (= {:current-round 0
            :next-player-id "b"
            :current-trick-cards [{:card a-card :player "a"}]
            :phase-ended? false
            :teams teams
            :scores {:Team1 0 :Team2 0}
            :events [{:event-type :card-played :player "a" :value {:card a-card}}]
            :players {"a" {:player-id "a" :player-index 0 :hand-cards [] :possible-cards [a-card] :possible-actions []}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card] :possible-cards [b-card] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card] :possible-cards [] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card] :possible-cards [] :possible-actions []}}}
           game-model))))

(deftest one-round-played
  (let [game-model (-> (model/init teams
                                   "a"
                                   (assoc-in initial-players ["a" :hand-cards] [a-card e-card f-card]))
                       (model/tick {:action-type :play-card :card a-card})
                       (model/tick {:action-type :play-card :card b-card})
                       (model/tick {:action-type :play-card :card c-card})
                       (model/tick {:action-type :play-card :card d-card}))]
    (is (= {:current-round 1
            :next-player-id "a"
            :current-trick-cards []
            :phase-ended? true
            :teams teams
            :scores {:Team1 64 :Team2 0}
            :events [{:event-type :card-played :player "a" :value {:card a-card}}
                     {:event-type :card-played :player "b" :value {:card b-card}}
                     {:event-type :card-played :player "c" :value {:card c-card}}
                     {:event-type :card-played :player "d" :value {:card d-card}}
                     {:event-type :round-won :player "a" :value {:card a-card :last-round? true}}]
            :players {"a" {:player-id "a"
                           :player-index 0
                           :hand-cards [e-card f-card]
                           :possible-cards [e-card f-card]
                           :possible-actions  [{:id "ask-for-trump" :action-type :ask-for-trump :target-player "c"}
                                               {:id "ask-for-half-trump:hearts"
                                                :action-type :ask-for-half-trump
                                                :suit :hearts
                                                :target-player "c"}
                                               {:id "ask-for-half-trump:clubs"
                                                :action-type :ask-for-half-trump
                                                :suit :clubs
                                                :target-player "c"}]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [] :possible-cards [b-card] :possible-actions []}
                      "c" {:player-id "c" :player-index 2 :hand-cards [] :possible-cards [c-card] :possible-actions []}
                      "d" {:player-id "d" :player-index 3 :hand-cards [] :possible-cards [d-card] :possible-actions []}}}
           game-model))))
