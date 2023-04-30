(ns huutopussi-simulation.sut-client
  (:require [cheshire.core :as json]
            [clojure.core.async :refer [chan go >!]]
            [clojure.tools.logging :as log]
            [hato.client :as hc]))

(def connect-timeout-in-ms 10000)
(def response-timeout-in-ms 120000)
(def sut-base-url "http://localhost:3000")

(def client (hc/build-http-client {:connect-timeout connect-timeout-in-ms
                                   :redirect-policy :always}))

(defn async-http-request [{:keys [endpoint url method payload-generator callback]} ctx]
  (let [response (chan)
        payload (when payload-generator
                  (when-let [generated (payload-generator ctx)]
                    (json/generate-string generated)))
        check-status (fn [resp]
                       (log/info "RESP" resp)
                       (go (>! response (callback resp ctx))))]
    (hc/request {:url (or url (str sut-base-url endpoint))
                 :method method
                 :http-client client
                 :timeout response-timeout-in-ms
                 :content-type :json
                 :throw-exceptions? false
                 :body payload
                 :as :auto
                 :async? true} check-status)
    response))

(comment
    (hc/request {:url "http://localhost:3000/index.html"
                 :method :get
                 :http-client client
                 :timeout response-timeout-in-ms
                 :content-type :json
                 :coerce :auto
                 :throw-exceptions? true
                 :body nil
                 :as :json})

    (hc/request {:url "http://localhost:3000/index.html"
                 :method :get
                 :http-client client
                 :timeout response-timeout-in-ms
                 :content-type :json
                 ;:coerce :auto
                 :throw-exceptions? true
                 :body nil
                 :as :auto
                 :async? true} (fn [r] (log/info "RESP" r)))
    )
