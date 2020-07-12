(ns ^:figwheel-hooks huutopussi-beacon.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [goog.dom :as gdom]
            [reagent.dom :as rdom]
            [reagent.core :as reagent :refer [atom]]))

(println "This text is printed from src/huutopussi_beacon/core.cljs. Go ahead and edit it and see reloading in action.")

(println "Current url:" (str (-> js/window .-location)))

(defn multiply [a b] (* a b))

(defonce dev-mode? (= "http://localhost:9500/" (str (-> js/window .-location))))

(println "Dev mode?" dev-mode?)

(defonce api-url (if dev-mode?
                   "http://localhost:3000/api"
                   "/api"))

(defonce app-state (atom {:player-name nil
                          :state :enter-name}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn- match-finder []
  [:div
   [:p (str "Finding match for player: " (:player-name @app-state))]])

(defn- start-match-finding []
  (println "Finding match")
  (swap! app-state assoc :state :finding-match)
  (go (let [response (<! (http/get (str api-url "/find-match/" (:player-name @app-state))
                                   {:with-credentials? false}))]
        (prn (:status response)))))

(defn match-start []
  [:div
   [:label "Enter your name"]
   [:input {:type "text"
            ; :value (:player-name @app-state)}]
            :on-change #(swap! app-state assoc :player-name (-> % .-target .-value))}]
   [:button {:on-click start-match-finding} "Find Match!"]])
   ;[:button {:on-click #(println "Clicked")} "Find Match!"]])

(defn home []
  [:div
   [:h1 "Huutopussi"]
   (when (= :enter-name (:state @app-state))
     (match-start))
   (when (= :finding-match (:state @app-state))
     (match-finder))
   ])

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
