(ns beacon-server.game
  (:require [beacon-server.model.deck :as deck]
            [clojure.core.async :refer [chan go go-loop <! <!! >!]]
            ))

(defn get-cards-for-player-name [matches id player-name]
  ;TODO Check that game has been started
  ;TODO We should create unique ids for players. Players should not know each others ids
  (let [match (get @matches id)]
    (when-not (= :started (:status match))
      (throw (Exception. "Match status should be started")))
    (:hand-cards (first (filter #(= player-name (:player-id %)) (-> match :game-model :players))))))

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
      (seq same-suit-cards) (if (or trump-card-played? (empty? higher-same-suit-cards))
                              same-suit-cards
                              higher-same-suit-cards)
      (seq trump-suit-cards) (if (and trump-card-played? (seq higher-trump-suit-cards))
                               higher-trump-suit-cards
                               trump-suit-cards)
      :else player-cards)))

(defn choose-card [round-cards player-cards]
  (first (possible-cards round-cards player-cards nil)))

;TODO better impl for whole of this
(defn find-index [pred vec]
  (reduce-kv
    (fn [_ k v]
      (when (pred v)
        (reduced k)))
    nil
    vec))

(defn- cards-in-play-order [cards start-index]
  (let [[first-part second-part] (split-at start-index cards)]
      (concat second-part first-part)))

(defn- play-round [players start-player]
  (let [round-cards (loop [played-cards []
                           players-left (cards-in-play-order players start-player)]
                      (if (seq players-left)
                        (recur (conj played-cards (choose-card played-cards (first players-left)))
                               (rest players-left))
                        played-cards))
        win-card (winning-card round-cards nil)
        win-player (find-index #(some (partial = win-card) %) (vec players))]
    {:winning-card (winning-card round-cards nil)
     :winning-player win-player
     :trick-cards (map #(some (set %) round-cards) players)}))

(defn- play-card [{:keys [current-round next-player] :as game-model} {:keys [card] :as player-card}]
  (let [updated-game-model (-> game-model
                               (update :current-trick-cards conj {:player next-player :card card})
                               (update-in [:players next-player :hand-cards] (fn [hand-cards]
                                                                               (remove #(= card %) hand-cards))))
        possible-cards-for-next-player (fn [next-player]
                                         (possible-cards (map :card (:current-trick-cards updated-game-model))
                                                         (get-in updated-game-model [:players next-player :hand-cards])
                                                         nil))
        trick-ended? (= 4 (count (:current-trick-cards updated-game-model)))]
    (if trick-ended?
      (let [win-card (winning-card (map :card (:current-trick-cards updated-game-model)) nil)
            win-player (-> (filter #(= win-card (:card %)) (:current-trick-cards updated-game-model))
                           first
                           :player)]
        (-> updated-game-model
            (update :current-round inc)
            (assoc :current-trick-cards [])
            (assoc :win-card win-card)
            (assoc :next-player win-player)
            (assoc-in [:player win-player :possible-cards] (possible-cards-for-next-player win-player))))
      (let [next-player (inc (:next-player updated-game-model))] ;TODO This works only in first round
        (-> updated-game-model
            (assoc :next-player next-player)
            (assoc-in [:player next-player :possible-cards] (possible-cards-for-next-player next-player)))))))

(defn- init-game [player-ids shuffled-cards cards-per-player]
  (let [game-model {:current-round 0
                    :next-player 0
                    :current-trick-cards []
                    :players (mapv (fn [player-id cards]
                                     (let [hand-cards (take cards-per-player cards)]
                                       {:player-id player-id
                                        :hand-cards hand-cards
                                        :possible-cards hand-cards}))
                                   player-ids
                                   shuffled-cards)}]
    game-model))

(defn start [matches id]
  (let [match (get @matches id)
        shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
        game-model (init-game (mapv :name (:players match)) shuffled-cards 9)]
    (swap! matches #(update % id assoc :status :started :game-model game-model))
    (get @matches id)))

(let [shuffled-cards (deck/shuffle-for-four-players (deck/card-deck))
      game-model (init-game ["a" "b" "c" "d"] shuffled-cards 2)
      do-it (fn [{:keys [next-player players] :as game-model}]
              (let [card-to-play (-> (nth players next-player) :possible-cards first)]
                    (play-card game-model {:card card-to-play})))]
  (-> game-model
     (do-it)
     (do-it)
     (do-it)))
     ;(do-it)))
   ;  (do-it)))

;(let [input-chs [(mapv chan (range 4))
;      game-loop (go-loop []
;                         (let [input (<! input-ch)]
;                           (println "LOL" input)))]
;  (go (>! input-ch "A"))
;  (<!! game-loop))


;(let [match {:id "1" :players [{:name "A"}
;                               {:name "B"}
;                               {:name "C"}
;                               {:name "D"}]}
;      matches (atom {"1" match})]
;  (start matches "1")
;  (get-cards-for-player-name matches "1" "C"))


;(let [input-ch (chan)
;      game-loop (go-loop []
;                         (let [input (<! input-ch)]
;                           (println "LOL" input)))]
;  (go (>! input-ch "A"))
;  (<!! game-loop))



