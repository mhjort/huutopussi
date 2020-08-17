(ns ^:figwheel-hooks huutopussi-beacon.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! timeout]]
            [clojure.string :as string]
            [goog.dom :as gdom]
            [reagent.dom :as rdom]
            [reagent.core :as reagent :refer [atom]]))

(defn multiply [a b] (* a b))

(defonce dev-mode? (= "http://localhost:9500/" (str (-> js/window .-location))))

(println "Dev mode?" dev-mode?)

(defonce api-url (if dev-mode?
                   "http://localhost:3000/api"
                   "/api"))

(def image-path "/img/cards")

(defonce app-state (atom {:player-name nil
                          ;:state :started
                          ;:cards [{:suit "clubs" :text "8"} {:suit "spades" :text "A"} {:suit "diamonds" :text "10"}]}))
                          :state :enter-name}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn- wait-until-state [match-id expected-state]
  (go-loop []
           (<! (timeout 500))
           (let [url (str api-url "/match/" match-id)
                 {:keys [body status] :as response} (<! (http/get url {:with-credentials? false}))]
             (if (= 200 status)
               (if (= expected-state (:status body))
                 body
                 (recur))
               (throw (js/Error. (str "Match find failed with response: " response)))))))


(defn- call-find-match [player-name]
  (go (let [url (str api-url "/match")
            response (<! (http/post url
                                    {:json-params {:playerName player-name}
                                     :with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Call to url " url " failed with response: " response)))))))

(defn- get-cards [id player-name]
  (go (let [url (str api-url "/match/" id "/cards/" player-name)
            _ (println "Calling url" url)
            response (<! (http/get url {:with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Call to url " url " failed with response: " response)))))))

(defn- call-start-game [id]
  (go (let [response (<! (http/post (str api-url "/match/" id "/start" )
                                    {:json-params {}
                                     :with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Match find failed with response: " response)))))))

(defn card-url [{:keys [suit text]}]
  (str image-path "/" (if (= "10" text)
                        "T"
                        text)
       (subs (string/upper-case suit) 0 1)
       ".svg"))

(defn- play-game []
  (println "Finding match for")
  (swap! app-state assoc :state :finding-match)
  (go
    (let [{:keys [id] :as match} (<! (call-find-match (:player-name @app-state)))
          _ (println "Found match" match)
          matched-match (<! (wait-until-state id "matched"))
          _ (swap! app-state assoc :state :matched :match matched-match)
          am-i-declarer? (= (:player-name @app-state) (:declarer matched-match))
          _      (println "Matched" matched-match am-i-declarer?)
          _  (when am-i-declarer?
               _    (call-start-game id))
          _  (<! (wait-until-state id "started"))
          cards (<! (get-cards id (:player-name @app-state)))]
      (println "Got cards" cards)
      (swap! app-state assoc :state :started :cards cards))))

(defn- show-match-status []
  [:div
   (condp = (:state @app-state)
     :finding-match [:p (str "Finding match for player: " (:player-name @app-state))]
     :matched [:p (str "Found match with players" (map :name (-> @app-state :match :players)))]
     :started [:div [:p (str "Started match with players" (map :name (-> @app-state :match :players)) "with cards:")]
                (for [card (:cards @app-state)]
                       [:img {:src (card-url card) :width "225px" :height "315px"}])]
     )])

(defn- show-match-start []
  [:div
    [:label "Enter your name"]
    [:input {:type "text"
             :on-change #(swap! app-state assoc :player-name (-> % .-target .-value))}]
    [:button {:type "submit" :value "Start Match!" :on-click play-game} "Start Match!"]])


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
