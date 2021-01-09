(ns beacon-server.handler-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as cheshire]
            [ring.mock.request :as mock]
            [beacon-server.game :as game]
            [beacon-server.handler :refer [create-app]]))

(deftest test-app
  (testing "index-html redirect"
    (let [response ((create-app (atom {})) (mock/request :get "/"))]
      (is (= 302 (:status response)))
      (is (= "" (:body response) ""))))

  (testing "not-found route"
    (let [response ((create-app (atom {})) (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))

(defn request-with-matches [method url matches]
  (let [{:keys [body status]} ((create-app matches)
                               (mock/request method url))]
      {:status status :body (cheshire/parse-string body true)}))

(def match-with-one-player
  {:declarer "player-1"
   :status :waiting
   :players {"player-1" {:name "player-1-name"
                         :id "player-1"}}})

(def match-with-four-players
  {:declarer "player-1"
   :status :waiting
   :players {"player-1" {:name "player-1-name"
                         :id "player-1"}
             "player-2" {:name "player-2-name"
                         :id "player-2"}
             "player-3" {:name "player-3-name"
                         :id "player-3"}
             "player-4" {:name "player-4-name"
                         :id "player-4"}}})

(deftest match
  (testing "status"
    (let [{:keys [status body]} (request-with-matches :get "/api/match/match-1" (atom {"match-1" match-with-one-player}))]
      (is (= 200 status))
      (is (= {:id "match-1"
              :status "waiting"
              :declarer "player-1"
              :players [{:name "player-1-name"}]}
             body))))
  (testing "mark-ready-to-start"
    (let [{:keys [status body]} (request-with-matches :put
                                                      "/api/match/match-1/ready-to-start/player-1"
                                                      (atom {"match-1" match-with-one-player}))]
      (is (= 200 status))
      (is (= {:id "match-1", :status "started", :declarer "player-1", :players [{:name "player-1-name"}]}
             body)))))

(deftest game
  (let [matches (atom {"match-1" (assoc match-with-four-players :status :started)})]
    (testing "initial-game"
    (game/start matches "match-1")
    (is (= :started (:status (get @matches "match-1"))))
    (let [{:keys [status body]} (request-with-matches :get "/api/match/match-1/status/player-1" matches)]
      (is (= 200 status))
      (is (= {:current-round 0 :next-player-name "player-1-name" :events []}
             (select-keys body [:next-player-name :current-round :events])))))
    (testing "after-one-card-played"
      (let [{:keys [body]} (request-with-matches :get "/api/match/match-1/status/player-1" matches)
            player-a-card (-> body :hand-cards first)
            {:keys [status]} (request-with-matches :put "/api/match/match-1/play/player-1/card/0" matches)
            _ (is (= 200 status))
            _ (Thread/sleep 10)
            {:keys [status body]} (request-with-matches :get "/api/match/match-1/status/player-1" matches)
            _ (is (= 200 status))
            _ (is (= {:current-round 0 :next-player-name "player-2-name" :events [{:event-type "card-played" :player "player-1-name" :value player-a-card}]}
                     (select-keys body [:next-player-name :current-round :events])))
            {:keys [body]} (request-with-matches :get "/api/match/match-1/status/player-1?events-since=1" matches)]
        (is (= [] (:events body)))))))

