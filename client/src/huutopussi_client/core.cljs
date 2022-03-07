(ns ^:figwheel-hooks huutopussi-client.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [huutopussi-client.game-client :as game-client]
            [huutopussi-client.bot :as bot]
            [cljs.core.async :refer [<! timeout]]
            [goog.dom :as gdom]
            [cemerick.url :as url]
            [clojure.string :as string]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

(defonce query (:query (url/url (-> js/window .-location .-href))))

(defonce auto-play? (= "true" (get query "auto-play")))

(println "Autoplay?" auto-play?)

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
      (println "Matched match" id "with player id" player-id)
      (re-frame/dispatch [:matched [matched-match player-id]]))))

(defn play-game [{:keys [match-id player-name player-id]}]
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
                    phase
                    next-player-name
                    teams]} (<! (game-client/get-game-status match-id player-id))]
        (re-frame/dispatch [:game-status {:cards hand-cards
                                          :possible-cards possible-cards
                                          :possible-actions possible-actions
                                          :events events
                                          :scores scores
                                          :teams teams
                                          :phase phase
                                          :current-round (inc current-round) ;Server round is zero based
                                          :current-trump-suit current-trump-suit
                                          :next-player-name next-player-name
                                          :trick-cards current-trick-cards}])
        (when (and auto-play? (= player-name next-player-name))
          (when-let [bot-action (bot/choose-bot-action {:phase phase
                                                        :hand-cards hand-cards
                                                        :possible-cards possible-cards
                                                        :possible-actions possible-actions})]
            (re-frame/dispatch bot-action))))
      (<! (timeout 500))
      (recur))))

(defn- run-action [id value]
  (re-frame/dispatch [:player-action {:id id :value value}])
  false)

(defn- show-action-selection-box [{:keys [possible-values id]} title]
  ^{:key id}[:div [:span title]
             [:select {:defaultValue "Valitse"
                       :on-change #(run-action id (js/parseInt (.. % -target -value)))}
              [:option {:disabled true} "Valitse"]
              (for [possible-value possible-values]
                ^{:key possible-value}[:option {:value possible-value} possible-value])]])

(defn- show-action-trigger [{:keys [id]} title]
  ^{:key id}[:span " "
             [:a {:href "#"
                  :on-click #(run-action id nil)}
              title]])

(defn- show-give-cards [{:keys [id possible-values]}]
  (let [number-of-cards-to-give (-> possible-values first count)]
    ^{:key id}[:span (str "Anna tiimikaverillesi " number-of-cards-to-give " korttia.")]))

(defn- show-possible-trumps [{:keys [phase possible-actions]}]
  (when (= "marjapussi" phase)
    (for [{:keys [action-type suit target-player] :as action} possible-actions]
      (case action-type
        "declare-trump" (show-action-trigger action
                                             (str "Tee " (get suits-fi suit) "valtti!"))
        "ask-for-half-trump" (show-action-trigger action
                                                  (str "Kysy onko pelaajalla " target-player " " (get suits-fi suit) "puolikasta!"))
        "ask-for-trump" (show-action-trigger action
                                             (str "Kysy onko pelaajalla " target-player " valtti!"))))))

(defn- show-possible-bidding-actions [{:keys [phase possible-actions]}]
  (when (= "bidding" phase)
    (for [{:keys [action-type] :as action} possible-actions]
      (case action-type
        "place-bid" (show-action-selection-box action "Huuda valitsemasi pistemäärä!")
        "fold" (show-action-trigger action "Luovuta huuto")
        "give-cards" (show-give-cards action)
        "set-target-score" (show-action-selection-box action "Aseta jaon pistemäärätavoite")))))

(defn- show-next-player [player-name {:keys [next-player-name phase]}]
  (if (= player-name next-player-name)
    (let [your-turn-text (case phase
                           "bidding" "Sinun vuorosi"
                           "marjapussi" "Sinun vuorosi lyödä kortti")]
      [:b your-turn-text])
    (str "Odottaa pelaajaa " next-player-name)))

(defn- format-card [{:keys [text suit]} grammatical-case]
  (case grammatical-case
    :genitive (str (get suits-fi suit) (get card-text-genitive-fi text))
    :adessive (str (get suits-fi suit) (get card-text-adessive-fi text))))

(defn- show-teams [teams]
  (let [formatted-teams (map (fn [[team-name {:keys [total-score players]}]]
                               (str (name team-name) ": (" (string/join "," players) ") pisteet: " total-score))
                             teams)]
    (string/join " ja " formatted-teams)))

(defn- format-event [{:keys [event-type player value]}]
  (let [{:keys [card last-round? answer]} value
        trick-str (if last-round?
                    "viimeisen tikin"
                    "tikin")]
    (case event-type
      "bid-placed" (str player " huusi pistemäärän " value)
      "folded" (str player " luovutti huudon")
      "target-score-set" (str player " asetti tiimin tavoitteeksi " value " pistettä")
      "card-played" (str player " löi " (format-card card :genitive))
      "round-won" (str player " vei " trick-str " " (format-card card :adessive))
      "trump-declared" (str player " teki " (get suits-fi (:suit value)) "valtin")
      "asked-for-half-trump" (str player " kysyi onko tiimikaverilla " (get suits-fi (:suit value)) "puolikasta")
      "answered-to-half-trump" (str player " vastasi, että " (if answer
                                                               "löytyy"
                                                               "ei löydy"))
      "answered-to-trump" (str player " vastasi, että " (if answer
                                                          "löytyy valtti"
                                                          "ei löydy valttia"))
      "asked-for-trump" (str player " kysyi onko tiimikaverilla valttia"))))

(defn- events-view [phase]
  (let [events @(re-frame/subscribe [:events])
        ;TODO Maybe it would be better to have different views for different phases?
        phase-events (if phase
                       ((keyword phase) events)
                       [])]
    ^{:key "events"} [:section#events
                      [:h3 "Pelitapahtumat"]
                      [:ul
                       (for [event (take 3 (reverse phase-events))]
                         ^{:key event} [:li (format-event event)])]]))

(defn- show-game-scores [scores]
  (reduce (fn [m [team team-score]]
            (let [{:keys [current target]} team-score
                  target-str (if target
                               (str "/" target)
                               "")]
              (str m " " (name team) ": " current target-str)))
          ""
          scores))

(defn- show-match-view []
  (let [player-name @(re-frame/subscribe [:player-name])
        chosen-card-indexes @(re-frame/subscribe [:chosen-card-indexes])
        {:keys [scores
                current-trump-suit
                cards
                trick-cards
                phase
                teams
                current-round] :as game} @(re-frame/subscribe [:game])]
    (list
     ^{:key "match-info"} [:section#match-info
                           [:p (show-teams teams)]
                           [:h3 (str "Jako (" current-round ". tikki)")]
                           [:p (str "Jaon pisteet:" (show-game-scores scores))
                            (when current-trump-suit (list ", " (get suits-fi current-trump-suit game) "valtti"))]
                           [:p (show-next-player player-name game) (show-possible-trumps game)]
                           (show-possible-bidding-actions game)]
     ^{:key "main"} [:main
                     [:h3 "Käsikorttisi"]
                     [:ul#player-hand
                      (for [[index card] (doall (map-indexed vector cards))]
                        (let [card-chosen? ((set chosen-card-indexes) index)
                              border-style (if card-chosen?
                                             {:border-style "solid"
                                              :border-color "coral"}
                                             {})]
                          ^{:key (str "hand-" (format-card card :genitive))} [:li
                                                                              [:img {:on-click #(re-frame/dispatch [:player-card index])
                                                                                     :style border-style
                                                                                     :src (card-url card)
                                                                                     :width "170px"
                                                                                     :height "auto"}]]))]
                     [:h3 "Tikin kortit"]
                     [:ul#trick-cards {:style {:display "flex"}}
                      (for [{:keys [card player]} trick-cards]
                        ^{:key (str "trick-" (format-card card :genitive))}[:li {:style {:width "170px"}}
                                                                            [:div player]
                                                                            [:img {:src (card-url card)
                                                                                   :width "100%"
                                                                                   :height "auto"}]])]]
     (events-view phase)
     )))

(defn- show-match-status []
  (condp = @(re-frame/subscribe [:state-change])
    :finding-match [:p (str "Etsitään peliä pelaajalle " @(re-frame/subscribe [:player-name]))]
    :matched [:p (str "Löytyi peli pelaajille: " (map :name (:players @(re-frame/subscribe [:match]))))]
    :started (show-match-view)))

(defn- show-match-start []
  (when auto-play?
    (re-frame/dispatch [:start-matchmake (str "bot-" (rand-int 10000))]))
  (let [player-name (atom "")]
    (list
     ^{:key "startup-form"} [:div#startup-form
                             [:label "Syötä nimesi "]
                             [:input {:type "text"
                                      :on-change #(reset! player-name (-> % .-target .-value))}]
                             " "
                             [:button {:type "submit"
                                       :value "Käynnistä peli!"
                                       :on-click #(re-frame/dispatch [:start-matchmake @player-name])} "Käynnistä peli!"]]
     ^{:key "autoplay"}[:div
                        [:a {:href "?auto-play=true"} "Käynnistä botti"]])))

(defn home []
  (let [state (re-frame/subscribe [:state-change])]
    [:div
     [:h1 "Huutopussi"]
     (if (= :enter-name @state)
       (show-match-start)
       (show-match-status))]))

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
    {:play-game {:player-name (:player-name db)
                 :player-id (:player-id db)
                :match-id (-> db :match :id)}
     :db (assoc db :state :started)}))

(re-frame/reg-event-fx
  :game-status
  (fn [{:keys [db]} [_ game]]
    {:db (assoc db :game game)}))

(re-frame/reg-event-fx
 :player-card
 (fn [{:keys [db]} [_ card-index]]
   (case (-> db :game :phase)
     "bidding" (let [updated-card-indexes (distinct (conj (:chosen-card-indexes db) card-index))
                     ;TODO Do not hardcode number of cards to give
                     all-cards-chosen? (= 3 (count updated-card-indexes))
                     chosen-card-indexes (if all-cards-chosen?
                                           []
                                           updated-card-indexes)]
                 (cond-> {:db (assoc db :chosen-card-indexes chosen-card-indexes)}
                   ;TODO Do not hardcode number of cards to give
                   (= 3 (count updated-card-indexes)) (assoc :run-player-action {:match-id (-> db :match :id)
                                                                                 :player-id (:player-id db)
                                                                                 :action-id "give-cards"
                                                                                 :action-value updated-card-indexes})))
     "marjapussi" (let [card-to-play (nth (-> db :game :cards) card-index)
                        possible-cards (-> db :game :possible-cards)
                        is-possible-card? (boolean (some #{card-to-play} possible-cards))]
                    (if is-possible-card?
                      {:play-card {:match-id (-> db :match :id) :player-id (:player-id db) :card-index card-index}}
                      {:show-error {:message (str "Kortti " card-to-play " ei ole yksi pelattavista korteista " :possible-cards)}})))))

(re-frame/reg-event-fx
  :player-action
  (fn [{:keys [db]} [_ {:keys [id value]}]]
    ;TODO Check if user can actually do this
    {:run-player-action {:match-id (-> db :match :id) :player-id (:player-id db) :action-id id :action-value value}}))

(re-frame/reg-fx
  :show-error
  (fn [{:keys [message]}]
    (throw (js/Error. message))))

(re-frame/reg-fx
  :play-card
  (fn [{:keys [match-id player-id card-index]}]
    (game-client/play-card match-id player-id card-index)))

(re-frame/reg-fx
  :run-player-action
  (fn [{:keys [match-id player-id action-id action-value]}]
    (game-client/run-player-action match-id player-id action-id action-value)))

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
  :chosen-card-indexes
  (fn [db _]
    (:chosen-card-indexes db)))

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
