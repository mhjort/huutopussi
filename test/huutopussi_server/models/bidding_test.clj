(ns huutopussi-server.models.bidding-test
  (:require [clojure.test :refer [deftest is testing]]
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
                           :possible-actions [{:id "place-bid"
                                               :action-type :place-bid
                                               :possible-values model/possible-target-scores}]}
                      "b" {:player-id "b" :player-index 1 :hand-cards [b-card]}
                      "c" {:player-id "c" :player-index 2 :hand-cards [c-card]}
                      "d" {:player-id "d" :player-index 3 :hand-cards [d-card]}}}
           game-model))))

(deftest players-can-place-bids-and-fold
  (let [game-model (-> (model/init teams "a" initial-players)
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "a"
                                    :value 60}))]
    (testing "first player can place bid"
      (is (= {:current-round 0
              :next-player-id "b"
              :phase :bidding
              :phase-ended? false
              :teams teams
              :scores {:Team1 0 :Team2 0}
              :highest-bid 60
              :events [{:event-type :bid-placed :player "a" :value 60}]
              :players {"a" {:player-id "a"
                             :player-index 0
                             :hand-cards [a-card]
                             :possible-actions []}
                        "b" {:player-id "b"
                             :player-index 1
                             :hand-cards [b-card]
                             :possible-actions [{:id "place-bid"
                                                 :action-type :place-bid
                                                 :possible-values (range 65 420 5)}
                                                {:id "fold"
                                                 :action-type :fold}]}
                        "c" {:player-id "c" :player-index 2 :hand-cards [c-card]}
                        "d" {:player-id "d" :player-index 3 :hand-cards [d-card]}}}
             game-model)))
    (testing "second player can fold"
      (let [after-fold (model/tick game-model {:id "fold"
                                               :action-type :fold
                                               :player-id "b"})]
        (is (= {:current-round 0
                :next-player-id "c"
                :phase :bidding
                :phase-ended? false
                :teams teams
                :scores {:Team1 0 :Team2 0}
                :highest-bid 60
                :events [{:event-type :bid-placed :player "a" :value 60}
                         {:event-type :folded :player "b"}]
                :players {"a" {:player-id "a"
                               :player-index 0
                               :hand-cards [a-card]
                               :possible-actions []}
                          "b" {:player-id "b"
                               :player-index 1
                               :hand-cards [b-card]
                               :possible-actions []}
                          "c" {:player-id "c"
                               :player-index 2
                               :hand-cards [c-card]
                               :possible-actions [{:id "place-bid"
                                                   :action-type :place-bid
                                                   :possible-values (range 65 420 5)}
                                                  {:id "fold"
                                                   :action-type :fold}]}
                          "d" {:player-id "d" :player-index 3 :hand-cards [d-card]}}}
               after-fold))))))

(deftest folded-player-is-skipped-after-fold
  (let [game-model (-> (model/init teams "a" initial-players)
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "a"
                                    :value 60})
                       (model/tick {:id "fold"
                                    :action-type :fold
                                    :player-id "b"})
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "c"
                                    :value 70})
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "d"
                                    :value 80})
                       (model/tick {:id "fold"
                                    :action-type :fold
                                    :player-id "a"}))]
    (is (= {:current-round 0
            :next-player-id "c"
            :phase :bidding
            :phase-ended? false
            :teams teams
            :scores {:Team1 0 :Team2 0}
            :highest-bid 80
            :events [{:event-type :bid-placed :player "a" :value 60}
                     {:event-type :folded :player "b"}
                     {:event-type :bid-placed :player "c" :value 70}
                     {:event-type :bid-placed :player "d" :value 80}
                     {:event-type :folded :player "a"}]
            :players {"a" {:player-id "a"
                           :player-index 0
                           :hand-cards [a-card]
                           :possible-actions []}
                      "b" {:player-id "b"
                           :player-index 1
                           :hand-cards [b-card]
                           :possible-actions []}
                      "c" {:player-id "c"
                           :player-index 2
                           :hand-cards [c-card]
                           :possible-actions [{:id "place-bid"
                                               :action-type :place-bid
                                               :possible-values (range 85 420 5)}
                                              {:id "fold"
                                               :action-type :fold}]}
                      "d" {:player-id "d"
                           :player-index 3
                           :hand-cards [d-card]
                           :possible-actions []}}}
           game-model))))

(deftest folded-player-is-skipped-after-placing-bid
  (let [game-model (-> (model/init teams "a" initial-players)
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "a"
                                    :value 60})
                       (model/tick {:id "fold"
                                    :action-type :fold
                                    :player-id "b"})
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "c"
                                    :value 70})
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "d"
                                    :value 80})
                       (model/tick {:id "play"
                                    :action-type :place-bid
                                    :player-id "a"
                                    :value 85}))]
    (is (= {:current-round 0
            :next-player-id "c"
            :phase :bidding
            :phase-ended? false
            :teams teams
            :scores {:Team1 0 :Team2 0}
            :highest-bid 85
            :events [{:event-type :bid-placed :player "a" :value 60}
                     {:event-type :folded :player "b"}
                     {:event-type :bid-placed :player "c" :value 70}
                     {:event-type :bid-placed :player "d" :value 80}
                     {:event-type :bid-placed :player "a" :value 85}]
            :players {"a" {:player-id "a"
                           :player-index 0
                           :hand-cards [a-card]
                           :possible-actions []}
                      "b" {:player-id "b"
                           :player-index 1
                           :hand-cards [b-card]
                           :possible-actions []}
                      "c" {:player-id "c"
                           :player-index 2
                           :hand-cards [c-card]
                           :possible-actions [{:id "place-bid"
                                               :action-type :place-bid
                                               :possible-values (range 90 420 5)}
                                              {:id "fold"
                                               :action-type :fold}]}
                      "d" {:player-id "d"
                           :player-index 3
                           :hand-cards [d-card]
                           :possible-actions []}}}
           game-model))))

(deftest full-bidding-round
  (let [game-model (-> (model/init teams "a" initial-players)
                       (model/tick {:id "play-bid"
                                    :action-type :place-bid
                                    :player-id "a"
                                    :value 60})
                       (model/tick {:id "fold"
                                    :action-type :fold
                                    :player-id "b"})
                       (model/tick {:id "fold"
                                    :action-type :fold
                                    :player-id "c"})
                       (model/tick {:id "fold"
                                    :action-type :fold
                                    :player-id "d"}))]
    (testing "bidding winner can set target score"
      (is (= {:current-round 0
              :next-player-id "a"
              :phase :bidding
              :phase-ended? false
              :teams teams
              :scores {:Team1 0 :Team2 0}
              :highest-bid 60
              :events [{:event-type :bid-placed :player "a" :value 60}
                       {:event-type :folded :player "b"}
                       {:event-type :folded :player "c"}
                       {:event-type :folded :player "d"}]
              :players {"a" {:player-id "a"
                             :player-index 0
                             :hand-cards [a-card]
                             :possible-actions [{:id "set-target-score"
                                                 :action-type :set-target-score
                                                 :possible-values (range 60 420 5)}]}
                        "b" {:player-id "b"
                             :player-index 1
                             :hand-cards [b-card]
                             :possible-actions []}
                        "c" {:player-id "c"
                             :player-index 2
                             :hand-cards [c-card]
                             :possible-actions []}
                        "d" {:player-id "d"
                             :player-index 3
                             :hand-cards [d-card]
                             :possible-actions []}}}
             game-model)))
    (testing "phase ends after setting target score"
      (let [final-game-model (model/tick game-model {:id "set-target-score"
                                                     :action-type :set-target-score
                                                     :player-id "a"
                                                     :value 60})]
        (is (= {:current-round 0
                :next-player-id "a"
                :phase :bidding
                :highest-bid 60
                :phase-ended? true
                :teams (assoc-in teams [:Team1 :target-score] 60)
                :scores {:Team1 0 :Team2 0}
                :events [{:event-type :bid-placed :player "a" :value 60}
                         {:event-type :folded :player "b"}
                         {:event-type :folded :player "c"}
                         {:event-type :folded :player "d"}
                         {:event-type :target-score-set :player "a" :value 60}]
                :players {"a" {:player-id "a"
                               :player-index 0
                               :hand-cards [a-card]
                               :possible-actions []}
                          "b" {:player-id "b"
                               :player-index 1
                               :hand-cards [b-card]
                               :possible-actions []}
                          "c" {:player-id "c"
                               :player-index 2
                               :hand-cards [c-card]
                               :possible-actions []}
                          "d" {:player-id "d"
                               :player-index 3
                               :hand-cards [d-card]
                               :possible-actions []}}}
               final-game-model))))))
