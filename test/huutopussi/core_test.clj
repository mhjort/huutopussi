(ns huutopussi.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [huutopussi.core :as h]))

(defn- pick-card [text suit]
  (first (filter #(and (= text (:text %))
                       (= suit (:suit %))) (h/card-deck))))
(deftest winning-card
  (testing "Highest value card wins if all cards are from same suit"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "9" :hearts)
                 (pick-card "J" :hearts)]]
      (is (= "JH" (h/print-card (h/winning-card cards nil))))))
  (testing "Hignest value card from same suit wins"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "9" :hearts)
                 (pick-card "A" :clubs)]]
      (is (= "9H" (h/print-card (h/winning-card cards nil))))))
  (testing "Highest value card from trump suit wins"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "A" :clubs)
                 (pick-card "6" :clubs)]]
      (is (= "AC" (h/print-card (h/winning-card cards :clubs)))))))

(deftest possible-cards
  (testing "If no same suit card or trump suit card exists all cards go"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "9" :hearts)]
          player-cards [(pick-card "J" :clubs)
                        (pick-card "Q" :clubs)]]
      (is (= ["JC" "QC"] (map h/print-card (h/possible-cards cards player-cards nil))))))
  (testing "If higher same suit card but trump suit card already played all same suit cards go"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "9" :hearts)
                 (pick-card "6" :clubs)]
          player-cards [(pick-card "7" :hearts)
                        (pick-card "Q" :hearts)]]
      (is (= ["7H" "QH"] (map h/print-card (h/possible-cards cards player-cards :clubs))))))
  (testing "If multiple same suit cards, player must choose cards that have higher value than max played card"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "9" :hearts)]
          player-cards [(pick-card "J" :hearts)
                        (pick-card "7" :hearts)
                        (pick-card "Q" :hearts)]]
      (is (= ["JH" "QH"] (map h/print-card (h/possible-cards cards player-cards nil))))))
  (testing "If no same suit cards, player must choose trump suit card"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "9" :hearts)]
          player-cards [(pick-card "J" :diamonds)
                        (pick-card "7" :diamonds)
                        (pick-card "6" :clubs)]]
      (is (= ["6C"] (map h/print-card (h/possible-cards cards player-cards :clubs))))))
  (testing "If no same suit cards and other player has played trump card, player must choose higher trump card"
    (let [cards [(pick-card "6" :hearts)
                 (pick-card "8" :hearts)
                 (pick-card "9" :clubs)]
          player-cards [(pick-card "J" :diamonds)
                        (pick-card "J" :clubs)
                        (pick-card "6" :clubs)]]
      (is (= ["JC"] (map h/print-card (h/possible-cards cards player-cards :clubs)))))))
