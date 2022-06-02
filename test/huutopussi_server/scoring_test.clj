(ns huutopussi-server.scoring-test
  (:require [clojure.test :refer [deftest is testing]]
            [huutopussi-server.scoring :as scoring]))

(def teams {:Team1 {:players ["a" "c"] :total-score 100}
            :Team2 {:players ["b" "d"] :total-score 100}})

(deftest when-target-is-met-all-team-scores-increase
  (is (= {:Team1 {:players ["a" "c"] :total-score 150}
          :Team2 {:players ["b" "d"] :total-score 160}}
         (scoring/update-team-scores teams {:Team1 {:current 50}
                                            :Team2 {:current 90 :target 60}}))))

(deftest when-target-is-not-met-other-team-score-decreases-by-target
  (is (= {:Team1 {:players ["a" "c"] :total-score 150}
          :Team2 {:players ["b" "d"] :total-score 0}}
         (scoring/update-team-scores teams {:Team1 {:current 50}
                                            :Team2 {:current 90 :target 100}}))))

(deftest get-winning-team
  (let [teams {:Team1 {:players ["a" "c"] :total-score 120}
               :Team2 {:players ["b" "d"] :total-score 150}}]
    (testing "Returns nil when non of the teams has enough points"
      (is (= nil (scoring/get-winning-team teams 200))))
    (testing "Returns the team that has enough points to win"
      (is (= :Team2 (scoring/get-winning-team teams 150))))
    (testing "Returns the team with more points when both teams have enough points"
      (is (= :Team2 (scoring/get-winning-team teams 120))))
    (testing "Returns nil when both teams have exactly the same score and have enough points"
      (is (= nil (scoring/get-winning-team (assoc-in teams [:Team1 :total-score] 150) 150))))))
