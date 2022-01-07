(ns huutopussi-client.bot)

(defn- index-of [x coll]
  (let [idx? (fn [i a] (when (= x a) i))]
    (first (keep-indexed idx? coll))))

(defn- play-first-possible-card [hand-cards possible-cards]
  (let [first-possible-card (first possible-cards)]
    (println "BOT: Playing first card" first-possible-card)
    [:player-card (index-of first-possible-card hand-cards)]))

(defn- run-random-possible-action [possible-actions]
  (let [action (first (shuffle possible-actions))]
    (println "BOT: Playing random action" action)
    [:player-action action]))

(defn- run-action-with-first-possible-value [{:keys [possible-values id]}]
  (let [first-possible-value (first possible-values)]
    (println "BOT: Playing action" id "with first possible value" first-possible-value)
    [:player-action {:id id :value first-possible-value}]))

(defn- choose-bidding-action [possible-actions]
  ;Bot folds always if possible
  (if-let [fold-action (first (filter #(= "fold" (:id %)) possible-actions))]
    [:player-action fold-action]
    (run-action-with-first-possible-value (first possible-actions))))

(defn- choose-marjapussi-action [{:keys [hand-cards possible-cards possible-actions]}]
  (if (seq possible-actions)
    (run-random-possible-action possible-actions)
    (when (seq possible-cards)
      (play-first-possible-card hand-cards possible-cards))))

(defn choose-bot-action [{:keys [phase possible-actions] :as params}]
  (case phase
    "bidding" (choose-bidding-action possible-actions)
    "marjapussi" (choose-marjapussi-action params)))

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

  ;Player with possible trump actions
  (choose-bot-action {:phase "marjapussi"
                      :possible-actions  [{:id "ask-for-trump" :action-type :ask-for-trump :target-player "c"}
                                          {:id "ask-for-half-trump:hearts"
                                           :action-type :ask-for-half-trump
                                           :suit :hearts
                                           :target-player "c"}
                                          {:id "ask-for-half-trump:clubs"
                                           :action-type :ask-for-half-trump
                                           :suit :clubs
                                           :target-player "c"}]})
  )
