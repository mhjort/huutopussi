(ns huutopussi-server.handler-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [cheshire.core :as cheshire]
            [ring.mock.request :as mock]
            [huutopussi-server.match :as match]
            [huutopussi-server.handler :refer [create-dev-app]]))

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
    (let [response ((create-dev-app (atom {})) (mock/request :get "/"))]
      (is (= 302 (:status response)))
      (is (= "" (:body response) ""))))

  (testing "not-found route"
    (let [response ((create-dev-app (atom {})) (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))

(defn request
  ([method url request-body]
   (let [app (create-dev-app matches)
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
   :teams {:Team1 {:total-score 0 :players ["player-1" "player-3"]}
           :Team2 {:total-score 0 :players ["player-2" "player-4"]}}
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
              :players [{:name "player-1-name"}]}
             body))))
  (testing "status with matched"
    (reset! matches {"match-1" match-with-four-players})
    (let [{:keys [status body]} (request :get "/api/match/match-1")]
      (is (= 200 status))
      (is (= {:id "match-1"
              :status "matched"
              :declarer "player-1"
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

(defn- fetch-first-hand-card []
  (-> (get-status "match-1" "player-1") :hand-cards first))

(deftest playing-real-match
  (reset! matches {"match-1" match-with-four-players})
  (testing "status when match has started"
    (match/start matches "match-1" match/initial-model-fns {:number-of-cards-swapped 1})
    (Thread/sleep 100)
    (is (= :started (:status (get @matches "match-1"))))
    (let [status (get-status "match-1" "player-1")]
      (is (= {:current-round 0
              :next-player-name "player-1-name"
              :events {:bidding []}
              :scores {:Team1 {:current 0} :Team2 {:current 0}}
              :teams {:Team1 {:total-score 0 :players ["player-1-name" "player-3-name"]}
                      :Team2 {:total-score 0 :players ["player-2-name" "player-4-name"]}}}
             (select-keys status [:next-player-name :current-round :events :scores :teams])))))
  (testing "status after bidding and setting target score"
    (run-action "match-1" "player-1" {:action-type "place-bid" :id "place-bid" :value 50})
    (run-action "match-1" "player-2" {:action-type "fold" :id "fold"})
    (run-action "match-1" "player-3" {:action-type "fold" :id "fold"})
    (run-action "match-1" "player-4" {:action-type "fold" :id "fold"})
    (run-action "match-1" "player-3" {:action-type "give-cards" :id "give-cards" :value [(fetch-first-hand-card)]})
    (run-action "match-1" "player-1" {:action-type "give-cards" :id "give-cards" :value [(fetch-first-hand-card)]})
    (run-action "match-1" "player-1" {:action-type "set-target-score" :id "set-target-score" :value 100})
    (let [status (get-status "match-1" "player-1")]
      (is (= {:scores {:Team1 {:current 0 :target 100}
                       :Team2 {:current 0}}}
             (select-keys status [:scores])))))
  (testing "status after one card played"
    (let [player-a-card (-> (get-status "match-1" "player-1") :hand-cards first)
          _ (is (not (nil? player-a-card)))
          _ (run-action "match-1" "player-1" {:action-type "play-card" :card-index 0})
          status (get-status "match-1" "player-1")]
      (is (= {:current-round 0
              :next-player-name "player-2-name"
              :events {:bidding [{:event-type "bid-placed"
                                  :player "player-1-name"
                                  :value 50}
                                 {:event-type "folded"
                                  :player "player-2-name"}
                                 {:event-type "folded"
                                  :player "player-3-name"}
                                 {:event-type "folded"
                                  :player "player-4-name"}
                                 {:event-type "bid-won"
                                  :player "player-1-name"
                                  :value 50}
                                 {:event-type "cards-given"
                                  :player "player-3-name"
                                  :value 1}
                                 {:event-type "cards-given"
                                  :player "player-1-name"
                                  :value 1}
                                 {:event-type "target-score-set"
                                  :player "player-1-name"
                                  :value 100}]
                       :marjapussi [{:event-type "card-played"
                                     :player "player-1-name"
                                     :value {:card player-a-card}}]}}
             (select-keys status [:next-player-name :current-round :events])))))
  (testing "server ignores action when it is not player turn anymore"
    (let [events-before (:events (get-status "match-1" "player-1"))]
      (run-action "match-1" "player-1" {:action-type "play-card" :card-index 0})
      (is (= events-before (:events (get-status "match-1" "player-1")))))))

(deftest playing-stubbed-match
  (reset! matches {"match-1" match-with-four-players})
  (let [started-phase1-rounds (atom 0)
        started-phase2-rounds (atom 0)]
    (testing "when game ends total score is updated and new game is started"
      (match/start matches
                   "match-1"
                   [{:model-init (fn [{:keys [next-player-id]} _]
                                   (swap! started-phase1-rounds inc)
                                   {:next-player-id next-player-id
                                    :teams {:Team1 {:score 0}
                                            :Team2 {:score 0 :target-score 2}}
                                    :phase :phase1
                                    :phase-ended? false})
                     :model-tick (fn [game-model _]
                                   (assoc game-model
                                          :phase-ended? true
                                          :next-player-id "player-2"
                                          :events [{:event-type :phase1-event}]))}
                    {:model-init (fn [{:keys [teams next-player-id]} _]
                                   (swap! started-phase2-rounds inc)
                                   {:next-player-id next-player-id
                                    :players {"player-2" {:possible-actions [{:id "continue-game"}
                                                                             {:id "end-game"}]}}
                                    :phase :phase2
                                    :teams (merge-with into teams
                                                       {:Team1 {:tricks 5}
                                                        :Team2 {:tricks 4}})
                                    :phase-ended? false})
                     :model-tick (fn [game-model {:keys [id]}]
                                   (-> game-model
                                       (update-in [:teams :Team1 :score] inc)
                                       (assoc :events [{:event-type :phase2-event}])
                                       (assoc :phase-ended? (= "end-game" id))))}]
                   {:time-before-starting-next-round 10
                    :number-of-cards-swapped 1
                    :points-to-win-match 3})
      (Thread/sleep 100)
      (is (= :started (:status (get @matches "match-1"))))
      (is (= {:Team1 {:total-score 0 :players ["player-1-name" "player-3-name"]}
              :Team2 {:total-score 0 :players ["player-2-name" "player-4-name"]}}
             (:teams (get-status "match-1" "player-1"))))
      (is (= 1 @started-phase1-rounds))
      (is (= 0 @started-phase2-rounds))
      (run-action "match-1" "player-1" {:id "set-target-score" :action-type :dummy})
      (is (= {:phase1 [{:event-type "phase1-event"}]
              :phase2 nil}
             (:events (get-status "match-1" "player-1"))))
      (is (= 1 @started-phase1-rounds))
      (is (= 1 @started-phase2-rounds))
      (run-action "match-1" "player-2" {:id "continue-game" :action-type :dummy})
      (is (= 1 @started-phase2-rounds))
      (is (= {:phase1 [{:event-type "phase1-event"}]
              :phase2 [{:event-type "phase2-event"}]}
             (:events (get-status "match-1" "player-1"))))
      (run-action "match-1" "player-2" {:id "end-game" :action-type :dummy})
      (is (= 2 @started-phase1-rounds))
      (is (= {:Team1 {:total-score 2 :players ["player-1-name" "player-3-name"]}
              :Team2 {:total-score -2 :players ["player-2-name" "player-4-name"]}}
             (:teams (get-status "match-1" "player-1")))))
    (testing "match ends when team1 has enough points"
      (run-action "match-1" "player-1" {:id "set-target-score" :action-type :dummy})
      (run-action "match-1" "player-2" {:id "continue-game" :action-type :dummy})
      (run-action "match-1" "player-2" {:id "end-game" :action-type :dummy})
      (is (= 2 @started-phase1-rounds))
      (is (= "Team1" (:winning-team (get-status "match-1" "player-1"))))
      (is (= {:Team1 {:total-score 3 :players ["player-1-name" "player-3-name"]}
              :Team2 {:total-score -4 :players ["player-2-name" "player-4-name"]}}
             (:teams (get-status "match-1" "player-1")))))))
