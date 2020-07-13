(ns beacon-server.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defn- find-match [player-name]
  (println "Finding match for" player-name)
  "Match found")


(defroutes app-routes
  (GET "/" [] (resp/redirect "/index.html"))
  (GET "/api/find-match/:player-name" [player-name] (find-match player-name))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
     (wrap-defaults site-defaults)
     (wrap-cors :access-control-allow-origin [#".*"]
                :access-control-allow-methods [:get :post :options])))
