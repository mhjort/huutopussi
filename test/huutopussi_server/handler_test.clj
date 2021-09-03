(ns huutopussi-server.handler-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as cheshire]
            [ring.mock.request :as mock]
            [huutopussi-server.match :as match]
            [huutopussi-server.handler :refer [create-app]]))

(def matches (atom {}))

(defn with-matches [t]
  (reset! matches {})
  (try
    (t)
    (finally
      (match/stop-match-loops matches))))

(use-fixtures :each with-matches)

(deftest test-app
  (testing "index-html redirect"
    (let [response ((create-app (atom {})) (mock/request :get "/"))]
      (is (= 302 (:status response)))
      (is (= "" (:body response) ""))))

  (testing "not-found route"
    (let [response ((create-app (atom {})) (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))

(defn request
  ([method url request-body]
   (let [app (create-app matches)
         {:keys [body status]} (cond-> (mock/request method url)
                                 request-body (mock/json-body request-body)
                                 true app)]
     {:status status :body (cheshire/parse-string body true)}))
  ([method url]
   (request method url nil)))

(def match-with-one-player
  {:declarer "player-1"
   :status :waiting
   :players {"player-1" {:name "player-1-name"
                         :id "player-1"}}})

(def match-with-four-players
  {:declarer "player-1"
   :status :matched
   :teams {:Team1 {:players ["player-1" "player-3"]}
           :Team2 {:players ["player-2" "player-4"]}}
   :players {"player-1" {:name "player-1-name"
                         :id "player-1"}
             "player-2" {:name "player-2-name"
                         :id "player-2"}
             "player-3" {:name "player-3-name"
                         :id "player-3"}
             "player-4" {:name "player-4-name"
                         :id "player-4"}}})

(deftest match-status
  (testing "status with waiting"
    (reset! matches {"match-1" match-with-one-player})
    (let [{:keys [status body]} (request :get "/api/match/match-1")]
      (is (= 200 status))
      (is (= {:id "match-1"
              :status "waiting"
              :declarer "player-1"
              :teams {}
              :players [{:name "player-1-name"}]}
             body))))
  (testing "status with matched"
    (reset! matches {"match-1" match-with-four-players})
    (let [{:keys [status body]} (request :get "/api/match/match-1")]
      (is (= 200 status))
      (is (= {:id "match-1"
              :status "matched"
              :declarer "player-1"
              :teams {:Team1 ["player-1-name" "player-3-name"]
                      :Team2 ["player-2-name" "player-4-name"]}
              :players [{:name "player-1-name"}
                        {:name "player-2-name"}
                        {:name "player-3-name"}
                        {:name "player-4-name"}]}
             body))))
  (testing "mark-ready-to-start"
    (reset! matches {"match-1" match-with-four-players})
    (let [{:keys [status body]} (request :put "/api/match/match-1/ready-to-start/player-1")]
      (is (= 200 status))
      (is (= {:id "match-1"
              :status "matched"
              :declarer "player-1"
              :teams {:Team1 ["player-1-name" "player-3-name"]
                      :Team2 ["player-2-name" "player-4-name"]}
              :players [{:name "player-1-name"}
                        {:name "player-2-name"}
                        {:name "player-3-name"}
                        {:name "player-4-name"}]}
             body)))))

(defn- run-action [match-id player-id action]
  (let [{:keys [status]} (request :put (str "/api/match/" match-id "/run/" player-id "/action") action)]
    (is (= 200 status))
    (Thread/sleep 200)))

(defn- get-status [match-id player-id]
  (let [{:keys [status body]} (request :get (str "/api/match/" match-id "/status/" player-id))]
    (is (= 200 status))
    body))

(deftest playing-a-match
  (reset! matches {"match-1" match-with-four-players})
  (testing "status when match has started"
    (match/start matches "match-1")
    (Thread/sleep 200)
    (is (= :started (:status (get @matches "match-1"))))
    (let [{:keys [status body]} (request :get "/api/match/match-1/status/player-1")]
      (is (= 200 status))
      (is (= {:current-round 0 :next-player-name "player-1-name" :events []}
             (select-keys body [:next-player-name :current-round :events])))))
  (testing "status after one card played"
    (let [{:keys [body]} (request :get "/api/match/match-1/status/player-1")
          player-a-card (-> body :hand-cards first)
          _ (run-action "match-1" "player-1" {:action-type "play-card" :card-index 0})
          status (get-status "match-1" "player-1")
          _ (is (= {:current-round 0
                    :next-player-name "player-2-name"
                    :events [{:event-type "card-played" :player "player-1-name" :value {:card player-a-card}}]}
                   (select-keys status [:next-player-name :current-round :events])))
          {:keys [body]} (request :get "/api/match/match-1/status/player-1?events-since=1")]
      (is (= [] (:events body)))))
  (testing "server ignores action when it is not player turn anymore"
    (let [events-before (:events (get-status "match-1" "player-1"))]
      (run-action "match-1" "player-1" {:action-type "play-card" :card-index 0})
      (is (= events-before (:events (get-status "match-1" "player-1")))))))
