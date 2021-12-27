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

(defn- choose-bidding-action [possible-actions]
  ;Bot folds always if possible
  (if-let [fold-action (first (filter #(= "fold" (:id %)) possible-actions))]
    [:player-action fold-action]
    (run-action-with-first-possible-value (first possible-actions))))

(defn choose-bot-action [{:keys [phase hand-cards possible-cards possible-actions]}]
  (case phase
    "bidding" (choose-bidding-action possible-actions)
    "marjapussi" (when (seq possible-cards)
                   (play-first-possible-card hand-cards possible-cards))))

(comment
  ;Starting player
  (choose-bot-action {:phase "bidding" :possible-actions [{:id "place-bid"
                                                           :possible-values [50 100]}]})
  ;2-4tht player
  (choose-bot-action {:phase "bidding" :possible-actions [{:id "place-bid"
                                                           :possible-values [50 100]}
                                                          {:id "fold"}]})
  ;Player who won the bidding
  (choose-bot-action {:phase "bidding" :possible-actions [{:id "set-target-score"
                                                           :possible-values [50 100]}]})
  )
