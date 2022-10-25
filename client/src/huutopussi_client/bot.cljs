(ns huutopussi-client.bot)

(defn- index-of [x coll]
  (let [idx? (fn [i a] (when (= x a) i))]
    (first (keep-indexed idx? coll))))

(defn- play-random-possible-card [hand-cards possible-cards]
  (let [card (rand-nth possible-cards)]
    (println "BOT: Playing random card" card)
    [:player-card (index-of card hand-cards)]))

(defn- run-random-possible-action [possible-actions]
  (let [action (first (shuffle possible-actions))]
    (println "BOT: Playing random action" action)
    [:player-action action]))

(defn- run-action-with-first-possible-value [{:keys [possible-values id]}]
  (let [first-possible-value (first possible-values)]
    (println "BOT: Playing action" id "with first possible value" first-possible-value)
    [:player-action {:id id :value first-possible-value}]))

(defn- weighted-sum [acc {:keys [text points] :as card}]
  (if card
    (+ acc
       points
       (if (or (= text "Q")
               (= text "K"))
         10
         0))
    acc))

(defn- choose-value-with-highest-points [possible-cards]
  (let [map-by-sum-points (reduce (fn [m cards]
                                    (assoc m (reduce weighted-sum 0 cards) cards))
                                  {}
                                  possible-cards)
        highest-sum (apply max (keys map-by-sum-points))]
    (get map-by-sum-points highest-sum)))

(defn- choose-bidding-action [possible-actions]
  ;Bot folds always if possible
  (if-let [fold-action (first (filter #(= "fold" (:id %)) possible-actions))]
    [:player-action fold-action]
    (condp = (-> possible-actions first :id)
      "give-cards" [:player-action {:id "give-cards"
                                    :value (choose-value-with-highest-points (-> possible-actions first :possible-values))}]
      (run-action-with-first-possible-value (first possible-actions)))))

(defn- choose-marjapussi-action [{:keys [hand-cards possible-cards possible-actions]}]
  (if (seq possible-actions)
    (run-random-possible-action possible-actions)
    (when (seq possible-cards)
      (play-random-possible-card hand-cards possible-cards))))

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


  ;Choose cards with max points
  (choose-bot-action {:phase "bidding"
                      :possible-actions [{:id "give-cards"
                                          :possible-values [[{:suit :hearts :value 15 :text "A" :points 11}
                                                             {:suit :hearts :value 6 :text "6" :points 0}]
                                                            [{:suit :hearts :value 14 :text "10" :points 10}
                                                             {:suit :hearts :value 13 :text "K" :points 4}]]}]})


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
