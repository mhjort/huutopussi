(ns beacon-server.handler
  (:require [compojure.core :refer [defroutes GET POST PUT]]
            [compojure.route :as route]
            [beacon-server.matchmake :as matchmake]
            [beacon-server.game :as game]
            [ring.util.response :as resp]
            [clojure.tools.logging :as log]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(def matches (atom {}))

(defn- find-match [{:keys [playerName]}]
  (matchmake/find-match matches playerName))

(defn- start-game [id]
  (let [started-match (game/start matches id)]
    (log/info "Started match" started-match)
     (resp/response {:ok true})))

(defn wrap-exception-handling
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "Failure in request" request)
        {:status 500 :body "Server error"}))))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/api/match/:id" [id] (resp/response (matchmake/get-match matches id)))
  (POST "/api/match" {:keys [body]} (resp/response (find-match body)))
  (POST "/api/match/:id/start" [id] (start-game id))
  (GET "/api/match/:id/cards/:player" [id player] (resp/response (game/get-cards-for-player-name matches id player)))
  (PUT "/api/match/:id/play/:player/card/:card-index" [id player card-index]
       (resp/response (game/play-card matches id player (Integer/parseInt card-index))))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
     ;Note! This should be first because middleware sets json content type header
     ;only if there are no other content type headers already present
     (wrap-json-response)
     (wrap-cors :access-control-allow-origin [#".*"]
                :access-control-allow-headers ["Origin" "X-Requested-With" "Content-Type" "Accept"]
                :access-control-allow-methods [:get :put :post :options])
     (wrap-defaults api-defaults)
     (wrap-json-body {:keywords? true})
     (wrap-exception-handling)))
