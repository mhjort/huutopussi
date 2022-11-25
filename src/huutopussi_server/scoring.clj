(ns huutopussi-server.scoring
  (:require [medley.core :as medley]))

;TODO Add case where leading team goes to Porvoo https://korttipeliopas.fi/huutopussi
(defn update-team-scores [teams round-teams]
  (let [leading-team-target (some (fn [[_ {:keys [target-score]}]]
                                    target-score) round-teams)]
    (reduce-kv (fn [m team team-stats]
                 (let [{:keys [score target-score tricks]} (team round-teams)
                       score-change (if target-score
                                      (if (>= score target-score)
                                        target-score
                                        (- target-score))
                                      (if (pos? tricks)
                                        score
                                        (- leading-team-target)))]
                   (assoc m team (update team-stats :total-score + score-change))))
               {} teams)))

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
