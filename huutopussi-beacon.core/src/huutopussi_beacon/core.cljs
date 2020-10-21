(ns ^:figwheel-hooks huutopussi-beacon.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [huutopussi-beacon.game-client :as game-client]
            [cljs.core.async :refer [<! timeout]]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [re-frame.core :as re-frame]
            [reagent.dom :as rdom]
            [reagent.core :as reagent]))

(defn multiply [a b] (* a b))

(def image-path "/img/cards")

(defonce app-state (reagent/atom {:player-name nil
                          ;:state :started
                          ;:cards [{:suit "clubs" :text "8"} {:suit "spades" :text "A"} {:suit "diamonds" :text "10"}]}))
                          :state :enter-name}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn card-url [{:keys [suit text]}]
  (str image-path "/" (if (= "10" text)
                        "T"
                        text)
       (subs (string/upper-case suit) 0 1)
       ".svg"))

(defn- play-game [coeffects [_ player-name]]
  (println "Finding match for" player-name)
  (swap! app-state assoc :state :finding-match)
  (go
    (let [{:keys [id status] :as match} (<! (game-client/call-find-match player-name))
          _ (println "Found match" match)
          matched-match (if (= "matched" status)
                          match
                          (<! (game-client/wait-until-state id "matched")))
          _ (swap! app-state assoc :state :matched :match matched-match)
          am-i-declarer? (= (:player-name @app-state) (:declarer matched-match))
          _      (println "Matched" matched-match am-i-declarer?)
          _  (when am-i-declarer?
               _    (game-client/call-start-game id))
          _  (<! (game-client/wait-until-state id "started"))
          cards (:hand-cards (<! (game-client/get-game-status id player-name)))]
      (println "Got cards first time" cards)
      (swap! app-state assoc :state :started :cards cards :match-id id)
      (loop []
        (<! (timeout 500))
        (let [{:keys [hand-cards
                      current-trick-cards
                      possible-cards
                      current-round
                      win-card
                      next-player-name]} (<! (game-client/get-game-status id player-name))]
          (swap! app-state assoc
                 :state :started
                 :cards hand-cards
                 :possible-cards possible-cards
                 :current-round (inc current-round) ;Server round is zero based
                 :next-player-name next-player-name
                 :trick-cards current-trick-cards))
        (recur)))))

(defn- try-to-play-card [card-index]
  (let [card-to-play (nth (:cards @app-state) card-index)
        is-possible-card? (boolean (some #{card-to-play} (:possible-cards @app-state)))]
    (when-not is-possible-card?
      (throw (js/Error.
               (str "Card " card-to-play " is not one of the possible cards " (:possible-cards @app-state)))))
  (game-client/play-card (:match-id @app-state) (:player-name @app-state) card-index)))

(defn- show-match-status []
  [:div
   (condp = (:state @app-state)
     :finding-match [:p (str "Finding match for player: " (:player-name @app-state))]
     :matched [:p (str "Found match with players" (map :name (-> @app-state :match :players)))]
     :started [:div
               [:p (str "Started match with players" (map :name (-> @app-state :match :players)))]
               [:p (str "Current round: " (:current-round @app-state)
                        ", waiting for player " (:next-player-name @app-state))]
               [:p (str "Your hand cards." (if (= (:player-name @app-state) (:next-player-name @app-state))
                                             "It is your turn to choose card"
                                             ""))]
               (for [[index card] (doall (map-indexed vector (:cards @app-state)))]
                 ^{:key card}[:img {:on-click (partial try-to-play-card index)
                                    :src (card-url card)
                                    :width "225px"
                                    :height "315px"}])
               [:p "Current trick cards"]
               (for [card (map :card (:trick-cards @app-state))]
                 ^{:key card}[:img {:src (card-url card)
                                    :width "225px"
                                    :height "315px"}])]
     )])

(defn- show-match-start []
  [:div
    [:label "Enter your name"]
    [:input {:type "text"
             :on-change #(swap! app-state assoc :player-name (-> % .-target .-value))}]
     [:button {:type "submit" :value "Start Match!" :on-click #(re-frame/dispatch [:start-matchmake (:player-name @app-state)])} "Start Match!"]])

(defn home []
  [:div
   [:h1 "Huutopussi"]
   (if (= :enter-name (:state @app-state))
     (show-match-start)
     (show-match-status))])

(defn mount [el]
  (rdom/render [home] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (mount el)))

(re-frame/reg-event-fx   ;; a part of the re-frame API
  :start-matchmake               ;; the kind of event
  play-game)

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
