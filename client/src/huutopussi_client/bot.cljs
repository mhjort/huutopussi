(ns huutopussi-client.bot)

(defn- index-of [x coll]
  (let [idx? (fn [i a] (when (= x a) i))]
    (first (keep-indexed idx? coll))))

(defn- play-first-possible-card [hand-cards possible-cards]
  (let [first-possible-card (first possible-cards)]
    (println "BOT: Playing first card" first-possible-card)
    [:player-card (index-of first-possible-card hand-cards)]))

(defn- run-action-with-first-possible-value [{:keys [possible-values id]}]
  (let [first-possible-value (first possible-values)]
    (println "BOT: Playing action" id "with first possible value" first-possible-value)
    [:player-action {:id id :value first-possible-value}]))

(defn choose-bot-action [{:keys [phase hand-cards possible-cards possible-actions]}]
  (case phase
    "bidding" (when-let [first-possible-action (first possible-actions)]
                (run-action-with-first-possible-value first-possible-action))
    "marjapussi" (when (seq possible-cards)
                   (play-first-possible-card hand-cards possible-cards))))

(comment
  (choose-bot-action {:phase "bidding" :possible-actions [{:id "set-target-score"
                                                           :possible-values [50 100]}]})
  )
