(ns ^:figwheel-hooks huutopussi-client.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [huutopussi-client.game-client :as game-client]
            [cljs.core.async :refer [<! timeout]]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

(defn multiply [a b] (* a b))

(def image-path "/img/cards")

(defn get-app-element []
  (gdom/getElement "app"))

(defn card-url [{:keys [suit text]}]
  (str image-path "/" (if (= "10" text)
                        "T"
                        text)
       (subs (string/upper-case suit) 0 1)
       ".svg"))

(defn start-matchmake [{:keys [player-name]}]
  (println "Finding match for" player-name)
  (go
    (let [{:keys [id status player-id] :as match} (<! (game-client/call-find-match player-name))
          _ (println "Found match" match "and created player id" player-id)
          ;TODO Race condition. Match can be already started
          matched-match (if (= "matched" status)
                          match
                          (<! (game-client/wait-until-state id "matched")))]
      (println "Matched")
      (re-frame/dispatch [:matched [matched-match player-id]]))))

(defn play-game [{:keys [match-id player-id]}]
  (go
    (loop []
      (let [{:keys [hand-cards
                    current-trick-cards
                    possible-cards
                    current-round
                    events
                    next-player-name]} (<! (game-client/get-game-status match-id player-id))]
        (re-frame/dispatch [:game-status {:cards hand-cards
                                           :possible-cards possible-cards
                                           :events events
                                           :current-round (inc current-round) ;Server round is zero based
                                           :next-player-name next-player-name
                                           :trick-cards current-trick-cards}]))
      (<! (timeout 500))
      (recur))))

(defn- show-match-status []
  [:div
   (condp = @(re-frame/subscribe [:state-change])
     :finding-match [:p (str "Finding match for player: " @(re-frame/subscribe [:player-name]))]
     :matched [:p (str "Found match with players" (map :name (:players @(re-frame/subscribe [:match]))))]
     :started (let [player-name @(re-frame/subscribe [:player-name])
                    match @(re-frame/subscribe [:match])
                    game @(re-frame/subscribe [:game])]
                (println "game is" game)
                [:div
                 [:p (str "Started match with players" (map :name (:players match)))]
                 [:p (str "Current round: " (:current-round game)
                          ", waiting for player " (:next-player-name game))]
                 [:p (str "Your hand cards." (if (= player-name (:next-player-name game))
                                               "It is your turn to choose card"
                                               ""))]
                 (for [[index card] (doall (map-indexed vector (:cards game)))]
                   ^{:key card}[:img {:on-click #(re-frame/dispatch [:player-card index])
                                      :src (card-url card)
                                      :width "225px"
                                      :height "315px"}])
                 [:p "Current trick cards"]
                 (for [card (map :card (:trick-cards game))]
                   ^{:key card}[:img {:src (card-url card)
                                      :width "225px"
                                      :height "315px"}])
                 ]))])

(defn- show-match-start []
  (let [player-name (atom "")]
    [:div
     [:label "Enter your name"]
     [:input {:type "text"
              :on-change #(reset! player-name (-> % .-target .-value))}]
     [:button {:type "submit" :value "Start Match!" :on-click #(re-frame/dispatch [:start-matchmake @player-name])} "Start Match!"]]))

(defn events-view []
  (let [events @(re-frame/subscribe [:events])]
    [:div
     [:ul
      (for [event events]
        (let [{:keys [event-type player value]} event
              card-str (str (:text value) " " (:suit value))
              event-text (if (= "card-played" event-type)
                           (str "Player " player " played card: " card-str)
                           (str "Player " player " won the tick with card " card-str))]
          [:li event-text]))]]))

(defn home []
  (let [state (re-frame/subscribe [:state-change])]
    [:div
     [:h1 "Huutopussi"]
     (if (= :enter-name @state)
       (show-match-start)
       (show-match-status))
     (events-view)]))

(defn mount [el]
  (re-frame/dispatch-sync [:change-state :enter-name])
  (rdom/render [home] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(re-frame/reg-event-fx
  :start-matchmake
  (fn [{:keys [db]} [_ player-name]]
    {:start-matchmake {:player-name player-name}
     :db (assoc db :state :finding-match :player-name player-name)}))

(re-frame/reg-event-fx
  :matched
  (fn [{:keys [db]} [_ [{:keys [id] :as match} player-id]]]
    {:wait-for-match {:player-id player-id :match-id id}
     :db (assoc db :state :matched :match match :player-id player-id)}))

(re-frame/reg-event-fx
  :game-started
  (fn [{:keys [db]} [_ _]]
    {:play-game {:player-name (:player-name db) :player-id (:player-id db) :match-id (-> db :match :id)}
     :db (assoc db :state :started)}))

(re-frame/reg-event-fx
  :game-status
  (fn [{:keys [db]} [_ game]]
    {:db (assoc db :game game)}))

(re-frame/reg-event-fx
  :player-card
  (fn [{:keys [db]} [_ card-index]]
    (let [card-to-play (nth (-> db :game :cards) card-index)
          possible-cards (-> db :game :possible-cards)
          is-possible-card? (boolean (some #{card-to-play} possible-cards))]
      (if is-possible-card?
        {:play-card {:match-id (-> db :match :id) :player-id (:player-id db) :card-index card-index}}
        {:show-error {:message (str "Card " card-to-play " is not one of the possible cards " :possible-cards)}}))))

(re-frame/reg-fx
  :show-error
  (fn [{:keys [message]}]
    (throw (js/Error. message))))

(re-frame/reg-fx
  :play-card
  (fn [{:keys [match-id player-id card-index]}]
    (game-client/play-card match-id player-id card-index)))

(re-frame/reg-fx
  :play-game
  play-game)

(re-frame/reg-fx
  :start-matchmake
  start-matchmake)

(re-frame/reg-fx
  :wait-for-match
  (fn [{:keys [player-id match-id]}]
    (go
      (<! (game-client/mark-as-ready match-id player-id))
      (<! (game-client/wait-until-state match-id "started"))
      (re-frame/dispatch [:game-started]))))

(re-frame/reg-sub
  :state-change
  (fn [db _]
    (:state db)))

(re-frame/reg-sub
  :player-name
  (fn [db _]
    (:player-name db)))

(re-frame/reg-sub
  :match
  (fn [db _]
    (:match db)))

(re-frame/reg-sub
  :game
  (fn [db _]
    (:game db)))

(re-frame/reg-sub
  :events
  (fn [db _]
    (-> db :game :events)))

(re-frame/reg-event-db ;; notice it's a db event
  :change-state
  (fn [db [_ state]]
    (println "updating state to" state)
    (assoc db :state state)))

(defonce init-application
  (do
    (println "Starting huutopussi application")
    (mount-app-element)
    true))

