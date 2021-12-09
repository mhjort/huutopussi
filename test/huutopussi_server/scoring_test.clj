(ns huutopussi-server.scoring-test
  (:require [clojure.test :refer [deftest is]]
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
