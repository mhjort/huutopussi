(ns huutopussi-simulation.sut-client
  (:require [cheshire.core :as json]
            [clojure.core.async :refer [chan go >! <!!]]
            [hato.client :as hc]))

(def connect-timeout-in-ms 10000)
(def response-timeout-in-ms 120000)
(def sut-base-url "http://localhost:3000")

(def client (hc/build-http-client {:connect-timeout connect-timeout-in-ms
                                   :redirect-policy :always}))

(defn http-request [{:keys [url method]} _]
  (hc/request {:url url
               :method method
               :http-client client
               :timeout response-timeout-in-ms
               :content-type :json
               :coerce :always
               :throw-exceptions? false
               :as :auto}))

(comment
  (:status (http-request {:url "https://www.google.com"
                          :method :get} {}))
)























(defn async-http-request [{:keys [endpoint url method payload-generator callback]} ctx]
  (let [return-channel (chan)
        payload (when payload-generator
                  (when-let [generated (payload-generator ctx)]
                    (json/generate-string generated)))
        handle-response (fn [response]
                          (go (>! return-channel (callback response ctx))))]
    (hc/request {:url (or url (str sut-base-url endpoint))
                 :method method
                 :http-client client
                 :timeout response-timeout-in-ms
                 :content-type :json
                 :coerce :always
                 :throw-exceptions? false
                 :body payload
                 :as :auto
                 :async? true} handle-response)
    return-channel))

(comment
  (<!! (async-http-request {:url "https://www.google.com"
                            :method :get
                            :callback (fn [{:keys [status]} _]
                                        (= 200 status))} {})))















