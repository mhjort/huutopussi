(ns huutopussi-server.scoring)

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
