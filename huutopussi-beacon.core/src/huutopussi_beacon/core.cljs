(ns ^:figwheel-hooks huutopussi-beacon.core
  (:require
   [goog.dom :as gdom]
   [reagent.dom :as rdom]
   [reagent.core :as reagent :refer [atom]]))

(println "This text is printed from src/huutopussi_beacon/core.cljs. Go ahead and edit it and see reloading in action.")

(defn multiply [a b] (* a b))

(defonce app-state (atom {:player-name nil
                          :state :enter-name}))

(defn get-app-element []
  (gdom/getElement "app"))

(defn- match-finder []
  [:div
   [:p (str "Finding match for player: " (:player-name @app-state))]])

(defn match-start []
  [:div
   [:label "Enter your name"]
   [:input {:type "text"
           ; :value (:player-name @app-state)}]
           :on-change #(swap! app-state assoc :player-name (-> % .-target .-value))}]
   [:button {:on-click #(swap! app-state assoc :state :finding-match)} "Find Match!"]])

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
