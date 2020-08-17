(ns beacon-server.handler
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [beacon-server.matchmake :as matchmake]
            [beacon-server.game :as game]
            [ring.util.response :as resp]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(def matches (atom {}))

(defn- find-match [{:keys [playerName]}]
  (matchmake/find-match matches playerName))

(defn- start-game [id]
  (let [started-match (game/start matches id)]
    (println "Started match" started-match)
    (resp/response {:ok true})))

(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/api/match/:id" [id] (resp/response (matchmake/get-match matches id)))
  (POST "/api/match" {:keys [body]} (resp/response (find-match body)))
  (POST "/api/match/:id/start" [id] (start-game id))
  (GET "/api/match/:id/cards/:player" [id player] (resp/response (game/get-cards-for-player-name matches id player)))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
     ;Note! This should be first because middleware sets json content type header
     ;only if there are no other content type headers already present
     (wrap-json-response)
     (wrap-cors :access-control-allow-origin [#".*"]
                :access-control-allow-methods [:get :post :options])
     (wrap-defaults api-defaults)
     (wrap-json-body {:keywords? true})))
