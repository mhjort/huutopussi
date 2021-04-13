(ns ^:figwheel-hooks huutopussi-client.game-client
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! timeout]]))

(defonce dev-mode? (= "http://localhost:9500/" (str (-> js/window .-location))))

(println "Dev mode?" dev-mode?)

(defonce api-url (if dev-mode?
                   "http://localhost:3000/api"
                   "/api"))

(defn wait-until-state [match-id expected-state]
  (go-loop []
           (let [url (str api-url "/match/" match-id)
                 {:keys [body status] :as response} (<! (http/get url {:with-credentials? false}))]
             (if (= 200 status)
               (if (= expected-state (:status body))
                 body
                 (do
                   (<! (timeout 500))
                   (recur)))
               (throw (js/Error. (str "Match find failed with response: " response)))))))

(defn call-find-match [player-name]
  (go (let [url (str api-url "/match")
            response (<! (http/post url
                                    {:json-params {:playerName player-name}
                                     :with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Call to url " url " failed with response: " response)))))))

(defn get-game-status [id player-name]
  (go (let [url (str api-url "/match/" id "/status/" player-name)
            response (<! (http/get url {:with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Call to url " url " failed with response: " response)))))))

(defn mark-as-ready [id player-name]
  (go (let [response (<! (http/put (str api-url "/match/" id "/ready-to-start/" player-name)
                                    {:json-params {}
                                     :with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Match mark-as-ready failed with response: " response)))))))

(defn- run-action [id player action]
  (go (let [url (str api-url "/match/" id "/run/" player "/action")
            response (<! (http/put url {:json-params action
                                        :with-credentials? false}))]
        (if (= 200 (:status response))
          (:body response)
          (throw (js/Error. (str "Call to url " url " failed with response: " response)))))))

(defn play-card [id player index]
  (println "Playing card: " index)
  (run-action id player {:action-type "play-card"
                         :card-index index}))

(defn declare-trump [id player suit]
  (println "Declaring trump: " suit)
  (run-action id player {:action-type "declare-trump"
                         :suit suit}))

(defn ask-for-trump [id player target-player]
  (println "Asking for trump from" target-player)
  (run-action id player {:action-type "ask-for-trump"}))

(defn ask-for-half-trump [id player target-player suit]
  (println "Asking for half trump" suit "from" target-player)
  (run-action id player {:action-type "ask-for-half-trump" :suit suit}))
