(ns ^:figwheel-hooks huutopussi-client.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [huutopussi-client.game-client :as game-client]
            [cljs.core.async :refer [<! timeout]]
            [goog.dom :as gdom]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

(defn multiply [a b] (* a b))

(def image-path "/img/cards")

(def suits-fi
  {"diamonds" "ruutu"
   "hearts" "hertta"
   "spades" "pata"
   "clubs" "risti"})

(def card-text-genitive-fi
  {"J" "jätkän"
   "A" "ässän"
   "K" "kuninkaan"
   "Q" "rouvan"
   "10" "kympin"
   "9" "ysin"
   "8" "kasin"
   "7" "seiskan"
   "6" "kutosen"})

(def card-text-adessive-fi
  {"J" "jätkällä"
   "A" "ässällä"
   "K" "kuninkaalla"
   "Q" "rouvalla"
   "10" "kympillä"
   "9" "ysillä"
   "8" "kasilla"
   "7" "seiskalla"
   "6" "kutosella"})

(defn get-app-element []
  (gdom/getElement "app"))

(defn card-url [{:keys [suit text]}]
  (let [postfix (condp = text
                  "J" "11"
                  "Q" "12"
                  "K" "13"
                  "A" "1"
                  text)]
    (str image-path "/" suit postfix ".png")))

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
                    possible-actions
                    current-round
                    current-trump-suit
                    events
                    scores
                    next-player-name]} (<! (game-client/get-game-status match-id player-id))]
        (re-frame/dispatch [:game-status {:cards hand-cards
                                          :possible-cards possible-cards
                                          :possible-actions possible-actions
                                          :events events
                                          :scores scores
                                          :current-round (inc current-round) ;Server round is zero based
                                          :current-trump-suit current-trump-suit
                                          :next-player-name next-player-name
                                          :trick-cards current-trick-cards}]))
      (<! (timeout 500))
      (recur))))

(defn- show-possible-trumps [{:keys [possible-actions]}]
  (let [declare-trump (fn [suit]
                        (re-frame/dispatch [:trump-suit suit])
                        false)
        ask-for-half-trump (fn [target-player suit]
                             (re-frame/dispatch [:half-trump-question [target-player suit]])
                             false)
        ask-for-trump (fn [target-player]
                        (re-frame/dispatch [:trump-question target-player])
                        false)]
    (for [{:keys [action-type suit target-player]} possible-actions]
      (case action-type
        "declare-trump" ^{:key suit}[:span " "
                                     [:a {:href "#"
                                          :on-click #(declare-trump suit)}
                                      (str "Tee " (get suits-fi suit) "valtti!")]]
        "ask-for-half-trump" ^{:key suit} [:span " "
                                           [:a {:href "#"
                                                :on-click #(ask-for-half-trump target-player suit)}
                                            (str "Kysy onko pelaajalla " target-player " " (get suits-fi suit) "puolikasta!")]]
        "ask-for-trump" ^{:key target-player} [:span " "
                                               [:a {:href "#"
                                                    :on-click #(ask-for-trump target-player)}
                                                (str "Kysy onko pelaajalla " target-player " valtti!")]]))))

(defn- show-next-player [player-name game]
  (if (= player-name (:next-player-name game))
    [:b "Sinun vuorosi lyödä kortti"]
    (str "Odottaa pelaajaa " (:next-player-name game))))

(defn- show-match-status []
  [:div
   (condp = @(re-frame/subscribe [:state-change])
     :finding-match [:p (str "Etsitään peliä pelaajalle " @(re-frame/subscribe [:player-name]))]
     :matched [:p (str "Löytyi peli pelaajille: " (map :name (:players @(re-frame/subscribe [:match]))))]
     :started (let [player-name @(re-frame/subscribe [:player-name])
                    match @(re-frame/subscribe [:match])
                    game @(re-frame/subscribe [:game])]
                [:div
                 [:p "Peli aloitettu joukkueilla: " (:teams match)]
                 [:p (:current-round game) ". tikki, pisteet: " (:scores game)
                     (when (:current-trump-suit game) (list ", " (get suits-fi (:current-trump-suit game)) "valtti"))]
                 [:p (show-next-player player-name game) (show-possible-trumps game)]
                 [:p "Käsikorttisi:"]
                 (for [[index card] (doall (map-indexed vector (:cards game)))]
                   ^{:key card}[:img {:on-click #(re-frame/dispatch [:player-card index])
                                      :src (card-url card)
                                      :width "170px"
                                      :height "auto"}])
                 [:p "Tikin kortit:"]
                 [:div {:style {:display "flex"}}
                  (for [{:keys [card player]} (:trick-cards game)]
                    ^{:key card}[:div {:style {:width "170px"}} [:img {:src (card-url card)
                                                                       :width "100%"
                                                                       :height "auto"}]
                                 [:span [:center player]]])]]))])

(defn- show-match-start []
  (let [player-name (atom "")]
    [:div
     [:label "Syötä nimesi "]
     [:input {:type "text"
              :on-change #(reset! player-name (-> % .-target .-value))}]
     " "
     [:button {:type "submit" :value "Käynnistä peli!" :on-click #(re-frame/dispatch [:start-matchmake @player-name])} "Käynnistä peli!"]]))

(defn format-event [{:keys [event-type player value]}]
  (let [{:keys [card last-round? answer]} value
        {:keys [text suit]} card
        card-str-genitive (str (get suits-fi suit) (get card-text-genitive-fi text))
        card-str-adessive (str (get suits-fi suit) (get card-text-adessive-fi text))
        trick-str (if last-round?
                   "viimeisen tikin"
                   "tikin")]
    (case event-type
      "card-played" (str player " löi " card-str-genitive)
      "round-won" (str player " vei " trick-str " " card-str-adessive)
      "trump-declared" (str player " teki " (get suits-fi (:suit value)) "valtin")
      "asked-for-half-trump" (str player " kysyi onko tiimikaverilla " (get suits-fi (:suit value)) "puolikasta")
      "answered-to-half-trump" (str player " vastasi, että " (if answer
                                                               "löytyy"
                                                               "ei löydy"))
      "answered-to-trump" (str player " vastasi, että " (if answer
                                                          "löytyy valtti"
                                                          "ei löydy valttia"))
      "asked-for-trump" (str player " kysyi onko tiimikaverilla valttia"))))

(defn events-view []
  (let [events @(re-frame/subscribe [:events])]
    (when events
      [:div
       [:p "Pelitapahtumat:"]
       [:ul
        (for [event (take 3 (reverse events))]
          ^{:key event} [:li (format-event event)])]])))

(defn home []
  (let [state (re-frame/subscribe [:state-change])]
    [:div
     [:h1 "Huutopussi"]
     (if (= :enter-name @state)
       (show-match-start)
       (show-match-status))
     (events-view)]))

(defn mount [el start-matchmake?]
  (when start-matchmake?
    (re-frame/dispatch-sync [:change-state :enter-name]))
  (rdom/render [home] el))

(defn mount-app-element [start-matchmake?]
  (when-let [el (get-app-element)]
    (mount el start-matchmake?)))

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
        {:show-error {:message (str "Kortti " card-to-play " ei ole yksi pelattavista korteista " :possible-cards)}}))))

(re-frame/reg-event-fx
  :trump-suit
  (fn [{:keys [db]} [_ suit]]
    ;TODO Check if user can actually do this
    {:declare-trump {:match-id (-> db :match :id) :player-id (:player-id db) :suit suit}}))

(re-frame/reg-event-fx
  :trump-question
  (fn [{:keys [db]} [_ target-player]]
    ;TODO Check if user can actually do this
    {:ask-for-trump {:match-id (-> db :match :id)
                     :player-id (:player-id db)
                     :target-player target-player}}))

(re-frame/reg-event-fx
  :half-trump-question
  (fn [{:keys [db]} [_ [target-player suit]]]
    ;TODO Check if user can actually do this
    {:ask-for-half-trump {:match-id (-> db :match :id)
                          :player-id (:player-id db)
                          :suit suit
                          :target-player target-player}}))

(re-frame/reg-fx
  :show-error
  (fn [{:keys [message]}]
    (throw (js/Error. message))))

(re-frame/reg-fx
  :play-card
  (fn [{:keys [match-id player-id card-index]}]
    (game-client/play-card match-id player-id card-index)))

(re-frame/reg-fx
  :declare-trump
  (fn [{:keys [match-id player-id suit]}]
    (game-client/declare-trump match-id player-id suit)))

(re-frame/reg-fx
  :ask-for-trump
  (fn [{:keys [match-id player-id target-player]}]
    (game-client/ask-for-trump match-id player-id target-player)))

(re-frame/reg-fx
  :ask-for-half-trump
  (fn [{:keys [match-id player-id target-player suit]}]
    (game-client/ask-for-half-trump match-id player-id target-player suit)))

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

(defn ^:after-load after-reload-callback []
  (mount-app-element false)
  (println "Code reloaded!"))

(defonce init-application
  (do
    (println "Starting huutopussi application")
    (mount-app-element true)
    true))
