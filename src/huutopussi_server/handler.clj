(ns huutopussi-server.handler
  (:require [compojure.core :refer [defroutes GET POST PUT]]
            [compojure.route :as route]
            [huutopussi-server.matchmake :as matchmake]
            [huutopussi-server.game :as game]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as resp]
            [clojure.tools.logging :as log]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(defn- find-match [matches {:keys [playerName]}]
  (matchmake/find-match matches playerName))

(defn wrap-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "Failure in request" request)
        {:status 500 :body "Server error"}))))

(defn- get-matches [request]
  (-> request :matches))

(defroutes app-routes
  (GET "/" []
       (resp/redirect "/index.html"))
  (GET "/api/match/:id" [id :as request]
       (resp/response (matchmake/get-match (get-matches request) id)))
  (POST "/api/match" {:keys [body] :as request}
        (resp/response (find-match (get-matches request) body)))
  (PUT "/api/match/:id/ready-to-start/:player" [id player :as request]
       (resp/response (matchmake/mark-as-ready-to-start (get-matches request) id player)))
  (GET "/api/match/:id/status/:player" [id player :as request]
       (resp/response (game/get-game-status (get-matches request) id player (-> request :params :events-since))))
  (PUT "/api/match/:id/run/:player/action" [id player :as request]
       (resp/response (game/run-action (get-matches request) id player (:body request))))
  (route/resources "/")
  (route/not-found "Not Found"))

(defn create-app [matches]
  (let [wrap-with-matches (fn [handler]
                            (fn [request]
                              (handler (assoc request :matches matches))))]
    (-> #'app-routes
        (wrap-with-matches)
        ;Note! This should be first because middleware sets json content type header
        ;only if there are no other content type headers already present
        (wrap-json-response)
        (wrap-cors :access-control-allow-origin [#".*"]
                   :access-control-allow-headers ["Origin" "X-Requested-With" "Content-Type" "Accept"]
                   :access-control-allow-methods [:get :put :post :options])
        (wrap-defaults api-defaults)
        (wrap-json-body {:keywords? true})
        (wrap-exception-handling))))

(defonce production-matches (atom {}))
(defonce prod-app (create-app production-matches))

(defn reset []
  (game/stop-game-loops production-matches)
  (reset! production-matches {}))

(defn start []
  (run-jetty prod-app {:join? false :port 3000}))

;For REPL Driven development
;(start)
;(reset)
;(game/stop-game-loops production-matches)
