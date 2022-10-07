(ns huutopussi-server.models.bidding
  (:require [clojure.set :as set]
            [clojure.math.combinatorics :as combo]
            [huutopussi-server.util :as util]))

(defn- set-target-score [{:keys [teams] :as game-model} player-id target-score]
  (let [player-team (get (util/teams-by-player teams) player-id)]
    (-> game-model
        (assoc-in [:teams player-team :target-score] target-score)
        (assoc-in [:players player-id :possible-actions] [])
        (update :events conj {:event-type :target-score-set :player player-id :value target-score})
        (assoc :phase-ended? true))))

(defn- get-next-player-ids [current-player-id players]
  (let [sorted-player-ids (sort-by #(get-in players [% :player-index]) (keys players))]
    ;Without limit of three this function returns lazy infinite sequence
    ;No need to return more than 3 next ids. It is safer to call this function with this limit
    (take 3 (rest (drop-while #(not= current-player-id %) (cycle sorted-player-ids))))))

(defn- get-non-folded-player-ids [{:keys [players events]}]
  (let [all-player-ids (set (keys players))
        folded-player-ids (map :player (filter #(= :folded (:event-type %)) events))
        non-folded-player-ids (set/difference all-player-ids (set folded-player-ids))]
    (sort-by #(get-in players [% :player-index]) non-folded-player-ids)))

(defn- get-next-non-folded-player-id [next-player-ids non-folded-player-ids]
  (first (filter (set non-folded-player-ids) next-player-ids)))

(defn- place-bid [{:keys [players] :as game-model} player-id bid-value]
  (let [non-folded-player-ids (get-non-folded-player-ids game-model)
        next-player-ids (get-next-player-ids player-id players)
        next-player-id (get-next-non-folded-player-id next-player-ids non-folded-player-ids)]
    (-> game-model
        (assoc-in [:players player-id :possible-actions] [])
        (assoc :next-player-id next-player-id)
        (assoc-in [:players next-player-id :possible-actions] [{:id "place-bid"
                                                                :action-type :place-bid
                                                                :possible-values (range (+ 5 bid-value)
                                                                                        420
                                                                                        5)}
                                                               {:id "fold"
                                                                :action-type :fold}])
        (update :events conj {:event-type :bid-placed :player player-id :value bid-value})
        (assoc :highest-bid bid-value))))

(defn- possible-cards-to-give [cards number-of-cards-to-give]
  (combo/combinations cards number-of-cards-to-give))

(defn- fold [{:keys [highest-bid players teams options] :as game-model} player-id]
  (let [updated-game-model (update game-model :events conj {:event-type :folded :player player-id})
        non-folded-player-ids (get-non-folded-player-ids updated-game-model)
        next-player-ids (get-next-player-ids player-id players)
        next-non-folded-player-id (get-next-non-folded-player-id next-player-ids non-folded-player-ids)
        bidding-ended? (= 1 (count non-folded-player-ids))
        next-player-id (if bidding-ended?
                         (util/team-mate-for-player next-non-folded-player-id teams)
                         next-non-folded-player-id)
        next-player-hand-cards (get-in players [next-player-id :hand-cards])
        generated-events (cond-> [{:event-type :folded :player player-id}]
                           bidding-ended? (conj {:event-type :bid-won
                                                 :player next-non-folded-player-id
                                                 :value highest-bid}))
        possible-actions (if bidding-ended?
                           [{:id "give-cards"
                             :action-type :give-cards
                             :possible-values (possible-cards-to-give next-player-hand-cards
                                                                      (:number-of-cards-swapped options))}]
                           [{:id "place-bid"
                             :action-type :place-bid
                             :possible-values (range (+ 5 highest-bid)
                                                     420
                                                     5)}
                            {:id "fold"
                             :action-type :fold}])]
    (-> game-model
        (assoc-in [:players player-id :possible-actions] [])
        (assoc :next-player-id next-player-id)
        (assoc-in [:players next-player-id :possible-actions] possible-actions)
        (update :events (comp vec concat) generated-events))))

;TODO Read max score from options
(def possible-target-scores (range 50 420 5))

(defn- give-cards [{:keys [highest-bid teams players options] :as game-model} player-id cards-to-give]
  (let [bid-winner-id (first (get-non-folded-player-ids game-model))
        next-player-id bid-winner-id
        target-player-id (util/team-mate-for-player player-id teams)
        ;TODO Do not use index for selecting cards, this is error prone
        target-player-hand-cards (vec (concat (get-in players [target-player-id :hand-cards]) cards-to-give))
        possible-actions (if (= player-id bid-winner-id)
                           [{:id "set-target-score"
                             :action-type :set-target-score
                             :possible-values (range highest-bid
                                                     420
                                                     5)}]
                           [{:id "give-cards"
                             :action-type :give-cards
                             :possible-values (possible-cards-to-give target-player-hand-cards
                                                                      (:number-of-cards-swapped options))}])]
  ;TODO Emit new event
    (-> game-model
        (assoc-in [:players player-id :possible-actions] [])
        ;TODO Do not use index for selecting cards, this is error prone
        (update-in [:players player-id :hand-cards] #(vec (remove (set cards-to-give) %)))
        (assoc :next-player-id next-player-id)
        ;TODO sort cards again somewhere
        (assoc-in [:players target-player-id :hand-cards] target-player-hand-cards)
        (assoc-in [:players next-player-id :possible-actions] possible-actions)
        (update :events conj {:event-type :cards-given :player player-id :value (count cards-to-give)}))))

(defn tick [game-model {:keys [action-type player-id value]}]
  (case action-type
    :place-bid (place-bid game-model player-id value)
    :fold (fold game-model player-id)
    :give-cards (give-cards game-model player-id value)
    :set-target-score (set-target-score game-model player-id value)))

(defn init [{:keys [teams next-player-id players]} options]
  {:teams teams
   :options options
   :players (assoc-in players [next-player-id :possible-actions] [{:id "place-bid"
                                                                   :action-type :place-bid
                                                                   :possible-values possible-target-scores}])
   :next-player-id next-player-id
   ;TODO Maybe this should not be set in bidding phase?
   :scores (reduce-kv (fn [m team _]
                        (assoc m team 0))
                      {}
                      teams)
   ;TODO Maybe this should not be set in bidding phase?
   :current-round 0
   :events []
   :phase :bidding
   :phase-ended? false})
