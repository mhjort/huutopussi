(ns huutopussi-server.models.bidding
  (:require [clojure.set :as set]
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

(defn- fold [{:keys [highest-bid players] :as game-model} player-id]
  (let [updated-game-model (update game-model :events conj {:event-type :folded :player player-id})
        non-folded-player-ids (get-non-folded-player-ids updated-game-model)
        next-player-ids (get-next-player-ids player-id players)
        next-player-id (get-next-non-folded-player-id next-player-ids non-folded-player-ids)
        bidding-ended? (= 1 (count non-folded-player-ids))
        possible-actions (if bidding-ended?
                           [{:id "set-target-score"
                             :action-type :set-target-score
                             :possible-values (range highest-bid 420 5)}]
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
        (update :events conj {:event-type :folded :player player-id}))))

(def possible-target-scores (range 50 420 5))

(defn tick [game-model {:keys [action-type player-id value]}]
  (case action-type
    :place-bid (place-bid game-model player-id value)
    :fold (fold game-model player-id)
    :set-target-score (set-target-score game-model player-id value)))

(defn init [teams starting-player players]
  {:teams teams
   :players (assoc-in players [starting-player :possible-actions] [{:id "place-bid"
                                                                    :action-type :place-bid
                                                                    :possible-values possible-target-scores}])
   :next-player-id starting-player
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
