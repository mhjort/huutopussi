(ns huutopussi-server.model
  (:require [huutopussi-server.deck :as deck]
            [medley.core :as medley]))

(defn winning-card [cards trump-suit]
  (let [round-suit (:suit (first cards))
        suit-matches #(= %1 (:suit %2))
        trump-suit-cards (if trump-suit
                           (filter (partial suit-matches trump-suit) cards)
                           [])
        potential-cards (if (seq trump-suit-cards)
                          trump-suit-cards
                          (filter (partial suit-matches round-suit) cards))]
    (first (sort-by :value #(compare %2 %1) potential-cards))))

(defn possible-cards [round-cards player-cards trump-suit]
  (let [round-suit (:suit (first round-cards))
        winning-card (winning-card round-cards trump-suit)
        trump-card-played? (= trump-suit (:suit winning-card))
        suit-matches #(= %1 (:suit %2))
        same-suit-cards (filter (partial suit-matches round-suit) player-cards)
        higher-same-suit-cards (filter #(> (:value %) (:value winning-card)) same-suit-cards)
        trump-suit-cards (if trump-suit
                           (filter (partial suit-matches trump-suit) player-cards)
                           [])
        higher-trump-suit-cards (filter #(> (:value %) (:value winning-card)) trump-suit-cards)]
    (cond
      (empty? round-cards) player-cards
      (seq same-suit-cards) (if (or trump-card-played? (empty? higher-same-suit-cards))
                              same-suit-cards
                              higher-same-suit-cards)
      (seq trump-suit-cards) (if (and trump-card-played? (seq higher-trump-suit-cards))
                               higher-trump-suit-cards
                               trump-suit-cards)
      :else player-cards)))

(defn- select-next-player-id [current-index players]
  (let [possible-index (inc current-index)
        next-player-index (if (= (count players) possible-index)
                            0
                            possible-index)
        next-player (first (filter #(= next-player-index (:player-index %)) (vals players)))]
    (:player-id next-player)))

(defn calculate-scores [{:keys [events teams]}]
  (let [team-by-player (reduce-kv (fn [m k [p1 p2]]
                                    (assoc m p1 k p2 k))
                                  {}
                                  teams)
        initial-scores (reduce-kv (fn [m team _]
                                    (assoc m team 0))
                                  {}
                                  teams)]
    (:scores (reduce (fn [m {:keys [event-type value player]}]
                       (let [team (get team-by-player player)]
                         (condp = event-type
                           :card-played (update m :cards conj (:card value))
                           :round-won (let [extra-trick-points (if (:last-round? value)
                                                                 20
                                                                 0)
                                            trick-points (+ extra-trick-points
                                                            (reduce + (map :points (:cards m))))]
                                        (-> (assoc m :cards [])
                                            (update-in [:scores team] + trick-points)))
                           :trump-declared (let [trump-points (get-in deck/all-suits [(:suit value) :trump-points])]
                                             (update-in m [:scores team] + trump-points))
                           m)))
                     {:scores initial-scores}
                     events))))

(defn- same-suit-king-or-queen-count [cards]
  (let [king-or-queen? #(some #{%} #{12 13})]
    (->> cards
         (reduce (fn [m {:keys [suit value]}]
                   (if (king-or-queen? value)
                     (update m suit inc)
                     m))
                 {:hearts 0 :clubs 0 :spades 0 :diamonds 0}))))

(defn- possible-trumps [cards]
  (->> (same-suit-king-or-queen-count cards)
       (filter (fn [[_ king-or-queen-count]] (= 2 king-or-queen-count)))
       (map (fn [[suit _]] suit))))

(defn- possible-half-trumps [cards]
  (->> (same-suit-king-or-queen-count cards)
       (filter (fn [[_ king-or-queen-count]] (= 1 king-or-queen-count)))
       (map (fn [[suit _]] suit))))

(defn- already-declared-trump-suits [events]
  (->> events
      (filter #(= :trump-declared (:event-type %)))
      (map #(get-in % [:value :suit]))))

(defn- team-mate-for-player [player-id teams]
  (->> (filter #(some #{player-id} %) (vals teams))
       (first)
       (remove #{player-id})
       (first)))

(defn- possible-actions-for-player [player-id {:keys [teams events] :as game-model}]
  (let [player-hand-cards (get-in game-model [:players player-id :hand-cards])
        team-mate-player-id (team-mate-for-player player-id teams)
        possible-player-trumps (remove (set (already-declared-trump-suits events)) (possible-trumps player-hand-cards))
        possible-player-half-trumps (remove (set (already-declared-trump-suits events)) (possible-half-trumps player-hand-cards))]
    (if (seq possible-player-trumps)
      (map (fn [suit] {:action-type "declare-trump" :suit suit})
           possible-player-trumps)
      (let [previous-event (last events)
            ask-for-trump-actions (when (< 1 (count player-hand-cards)) ;At least two cards left
                                    [{:action-type "ask-for-trump" :target-player team-mate-player-id}])
            ;TODO Check that player can ask same question only once
            ask-for-half-trump-actions (map (fn [suit]
                                              {:action-type "ask-for-half-trump" :suit suit :target-player team-mate-player-id})
                                            possible-player-half-trumps)]
        (if (= :round-won (:event-type previous-event))
          (concat ask-for-trump-actions ask-for-half-trump-actions)
          [])))))

(defn- play-card [{:keys [next-player-id players current-trump-suit] :as game-model} {:keys [card]}]
  (let [reset-possible-actions #(assoc % :possible-actions [])
        updated-game-model (-> game-model
                               (update :players #(medley/map-vals reset-possible-actions %))
                               (update :current-trick-cards conj {:player next-player-id :card card})
                               (update :events conj {:event-type :card-played :player next-player-id :value {:card card}})
                               (update-in [:players next-player-id :hand-cards] (fn [hand-cards]
                                                                                     (vec (remove #(= card %) hand-cards)))))
        possible-cards-for-next-player (fn [next-player current-trick-cards]
                                         (possible-cards (mapv :card current-trick-cards)
                                                         (get-in updated-game-model [:players next-player :hand-cards])
                                                         current-trump-suit))
        trick-ended? (= (count players) (count (:current-trick-cards updated-game-model)))
        ;TODO Better check for this
        game-ended? (and trick-ended?
                         (empty? (get-in updated-game-model [:players next-player-id :hand-cards])))]
    (if trick-ended?
      (let [win-card (winning-card (map :card (:current-trick-cards updated-game-model)) current-trump-suit)
            win-player (-> (filter #(= win-card (:card %)) (:current-trick-cards updated-game-model))
                           first
                           :player)
            trick-ended-model (-> updated-game-model
                                  (update :current-round inc)
                                  (assoc :game-ended? game-ended?)
                                  (assoc :current-trick-cards [])
                                  (update :events conj {:event-type :round-won :player win-player :value {:card win-card :last-round? game-ended?}})
                                  (assoc :next-player-id win-player)
                                  (assoc-in [:players win-player :possible-cards] (possible-cards-for-next-player win-player [])))]
        (assoc-in trick-ended-model [:players win-player :possible-actions] (possible-actions-for-player win-player trick-ended-model)))
      (let [next-player-id (select-next-player-id (get-in updated-game-model [:players next-player-id :player-index])
                                                  (:players updated-game-model))]
        (-> updated-game-model
            (assoc :game-ended? game-ended?)
            (assoc :next-player-id next-player-id)
            (assoc-in [:players next-player-id :possible-cards]
                      (possible-cards-for-next-player next-player-id (:current-trick-cards updated-game-model))))))))

(defn- declare-trump [{:keys [next-player-id] :as game-model} {:keys [suit player-id]}]
  (let [hand-cards (get-in game-model [:players next-player-id :hand-cards])
        trump-event {:event-type :trump-declared :player player-id :value {:suit suit}}
        updated-game-model (-> game-model
                               (update :events conj trump-event)
                               (assoc :current-trump-suit suit))]
    (-> updated-game-model
        (assoc-in [:players next-player-id :possible-actions] (possible-actions-for-player next-player-id
                                                                                           updated-game-model))
        (assoc-in [:players next-player-id :possible-cards] (possible-cards [] hand-cards suit)))))

(defn- ask-for-trump [{:keys [next-player-id teams] :as game-model} {:keys [player-id]}]
  (let [target-player (team-mate-for-player player-id teams)
        player-hand-cards (get-in game-model [:players target-player :hand-cards])
        possible-trump (first (possible-trumps player-hand-cards))]
    (cond-> game-model
      true (update :events conj {:event-type :asked-for-trump
                                 :player next-player-id
                                 :value {:target-player target-player}})

      true (assoc-in [:players next-player-id :possible-actions] [])
      possible-trump (declare-trump {:suit possible-trump
                                     :player-id target-player}))))

(defn- ask-for-half-trump [{:keys [next-player-id teams] :as game-model} {:keys [player-id suit]}]
  (let [target-player (team-mate-for-player player-id teams)
        player-hand-cards (get-in game-model [:players target-player :hand-cards])
        possible-half-trumps (possible-half-trumps player-hand-cards)
        trump-can-be-made? (some #{suit} possible-half-trumps)]
    (cond-> game-model
      true (update :events conj {:event-type :asked-for-half-trump
                                 :player next-player-id
                                 :value {:target-player target-player :suit suit}})
      true (update :events conj {:event-type :answered-to-half-trump
                                 :player target-player
                                 :value {:answer trump-can-be-made? :suit suit}})
      true (assoc-in [:players next-player-id :possible-actions] [])
      trump-can-be-made? (declare-trump {:suit suit
                                         :player-id next-player-id}))))

(defn tick [game-model {:keys [action-type] :as action}]
  (case action-type
    :play-card (play-card game-model action)
    :declare-trump (declare-trump game-model action)
    :ask-for-half-trump (ask-for-half-trump game-model action)
    :ask-for-trump (ask-for-trump game-model action)))

(defn init [teams shuffled-cards]
  ;TODO Works only with exactly 2 teams with both having 2 players
  (let [[team1-first-player team1-second-player team2-first-player team2-second-player] (mapcat val teams)
        player-ids [team1-first-player team2-first-player team1-second-player team2-second-player]
        game-model {:current-round 0
                    :next-player-id (first player-ids)
                    :current-trick-cards []
                    :events []
                    :game-ended? false
                    :teams teams
                    :players (into {} (map (fn [[player-index player-id] cards]
                                             (let [hand-cards (vec cards)]
                                               [player-id {:player-id player-id
                                                           :player-index player-index
                                                           :hand-cards hand-cards
                                                           :possible-cards hand-cards
                                                           :possible-actions []}]))
                                           (map-indexed vector player-ids)
                                           shuffled-cards))}]
    game-model))

(defn play-test-game []
  ;(let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
  (let [shuffled-cards (deck/same-suit-for-four-players (deck/card-deck))
        game-model (init {"Team1" ["a" "b"]
                          "Team2" ["c" "d"]} (map #(take 9 %) shuffled-cards))
        _ (prn "Team mate" (team-mate-for-player "a" (:teams game-model)))
        do-first-action (fn [{:keys [next-player-id players] :as game-model}]
                          (if-let [action (-> players (get-in [next-player-id :possible-actions]) first)]
                            (if (= "declare-trump" (:action-type action))
                              (do
                                (prn "Player" next-player-id "declares" action)
                                (tick game-model {:action-type :declare-trump
                                                  :player-id next-player-id
                                                  :suit (:suit action)}))
                              (do
                                (prn "Player" next-player-id "Ask for trump from player" action)
                                (tick game-model {:action-type :ask-for-trump
                                                  :player-id next-player-id})))
                            game-model))
        play-first-possible-card (fn [{:keys [players next-player-id] :as game-model}]
                                   (let [card-to-play (first (get-in players [next-player-id :possible-cards]))]
                                     (prn "Player" next-player-id "plays" card-to-play)
                                     (tick game-model {:action-type :play-card :card card-to-play})))
        do-it (fn [game-model]
                (-> game-model
                    (do-first-action)
                    (do-first-action)
                    (play-first-possible-card)))
        end-result  (-> game-model
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                       (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                        (do-it)
                       (do-it))]
    end-result))

;(play-test-game)
;(calculate-scores (play-test-game))
