(ns huutopussi-simulation.core
  (:require [clj-gatling.core :as gatling]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go >! <!!]]
            [cheshire.core :as json]
            [hato.client :as hc])
  (:gen-class))

(def connect-timeout-in-ms 10000)
(def response-timeout-in-ms 120000)

(def client (hc/build-http-client {:connect-timeout connect-timeout-in-ms
                                   :redirect-policy :always}))

(defn create-input-params [user-id]
  {:playerName (str "player-" user-id)})

(defn- http-post [url create-input-params-fn {:keys [user-id] :as context}]
  (let [response (chan)
        payload (json/generate-string (create-input-params-fn user-id))
        check-status (fn [{:keys [status body]}]
                       (log/info "Got response with status" status "and body" body)
                       (let [updated-context (assoc context
                                                    :match-id (:id body)
                                                    ;TODO Huutopussi does not conform to json camelCase in response
                                                    :player-id (:player-id body))]
                         (go (>! response [(= 200 status) updated-context]))))]
    (hc/post url
             {:http-client client
              :timeout response-timeout-in-ms
              :content-type :json
              :coerce :always
              :throw-exceptions? false
              :body payload
              :as :json
              :async? true} check-status)
    response))

(defn- http-get [base-url {:keys [match-id] :as context}]
  (let [response (chan)
        check-status (fn [{:keys [status]}]
                       (let [updated-context context]
                         (go (>! response [(= 200 status) updated-context]))))]
    (hc/get (str base-url match-id)
            {:http-client client
             :timeout response-timeout-in-ms
             :content-type :json
             :coerce :always
             :throw-exceptions? false
             :as :json
             :async? true} check-status)
    response))

(comment
  (<!! (http-post "http://localhost:3000/api/match" create-input-params {:user-id "1"})))

(def simulation
  {:name "Huutopussi simulation"
   :scenarios [{:name "Player join"
                :steps [{:name "Start match make"
                         :request (partial http-post "http://localhost:3000/api/match" create-input-params)}
                        {:name "Get match status"
                         :request (partial http-get "http://localhost:3000/api/match/")}]}]})

(defn -main [& _]
  (gatling/run simulation
               {:concurrency 1
                :requests 1}))
