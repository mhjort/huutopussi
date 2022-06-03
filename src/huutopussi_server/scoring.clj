(ns huutopussi-server.scoring
  (:require [medley.core :as medley]))

(defn update-team-scores [teams round-scores]
  (reduce-kv (fn [m team team-stats]
               (let [{:keys [current target]} (team round-scores)
                     score-change (if target
                                    (if (>= current target)
                                      target
                                      (- target))
                                    current)]
                 (assoc m team (update team-stats :total-score + score-change))))
             {} teams))

(defn get-winning-team [teams score-to-win-the-match]
  (let [teams-who-have-enough-points (medley/filter-vals #(>= (:total-score %) score-to-win-the-match) teams)
        same-score-for-all-teams? (every? (fn [[_ {:keys [total-score]}]]
                                            (= (-> teams vals first :total-score) total-score)) teams)
        [team] (last (sort-by (fn [[_ {:keys [total-score]}]]
                                total-score) teams-who-have-enough-points))]
    ;; This is a bit tricky case.
    ;; If both teams have exactly the same points game will continue until one team has more points than the other.
    (when-not same-score-for-all-teams?
      team)))
