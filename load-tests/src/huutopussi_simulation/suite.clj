(ns huutopussi-simulation.suite
  (:require [huutopussi-simulation.sut-client :as sc]
            [trombi.core :as trombi]
            [clojure.core.async :refer [<!!]]
            [clojure.tools.logging :as log])
  (:import (java.time Duration)))

(def google-simulation
  {:name "Calling Google"
   :scenarios
   [{:name "Call google scenario"
     :steps [{:name "Call frontpage"
              :request (fn [ctx]
                         (sc/async-http-request {:url "https://www.google.com"
                                                 :method :get
                                                 :callback (fn [{:keys [status]} _]
                                                                (= 200 status))}
                                                ctx))}]}]})
(comment
  (trombi/run google-simulation {:concurrency 5 :requests 20})

  (trombi/run google-simulation {:concurrency 5 :duration (Duration/ofSeconds 1)})

  ;With Graphical report
  (require '[trombi-gatling-highcharts-reporter.core])
  (trombi/run google-simulation  {:concurrency 100 :reporters [trombi-gatling-highcharts-reporter.core/reporter]}))
























(defn- start-matchmake [original-context]
  (sc/async-http-request {:endpoint "/api/match"
                          :method :post
                          :payload-generator (fn [{:keys [user-id]}]
                                               {:playerName (str "player-" user-id)})
                          :callback (fn [{:keys [status body]} context]
                                      (let [updated-context (assoc context
                                                                   :match-id (:id body)
                                                                   :match-status (:status body)
                                                                   :player-id (:player-id body))]
                                        [(= 200 status) updated-context]))}
                         original-context))

(comment
  (<!! (start-matchmake {:user-id "5"})))







(defn- get-and-update-match-status [{:keys [match-id] :as original-context}]
  (log/info "Polling match status for match id" match-id)
  (sc/async-http-request {:endpoint (str "/api/match/" match-id)
                          :method :get
                          :callback (fn [{:keys [status body]} context]
                                      (let [match-status (:status body)
                                            updated-context (assoc context :match-status match-status)]
                                        [(= 200 status)
                                         updated-context]))}
                         original-context))

(comment
  (let [[_ context] (<!! (start-matchmake {:user-id "3"}))]
    (<!! (get-and-update-match-status context))))






(defn- mark-as-ready [{:keys [match-id player-id] :as original-context}]
  (log/info "Marking as ready to start for match id" match-id "and player id" player-id)
  (sc/async-http-request {:endpoint (str "/api/match/" match-id "/ready-to-start/" player-id)
                          :method :put
                          :callback (fn [{:keys [status]} context]
                                      (let [updated-context (assoc context :marked-as-ready? true)]
                                        [(= 200 status)
                                         updated-context]))}
                         original-context))

(def simulation
  {:name "Huutopussi simulation"
   :scenarios [{:name "Player joining"
                :steps [{:name "Start match make"
                         :request start-matchmake}
                        {:name "Get match status"
                         :request get-and-update-match-status}
                        {:name "Get match status"
                         :sleep-before (constantly 100)
                         :request get-and-update-match-status}
                        {:name "Mark as ready to play"
                         :request mark-as-ready}]}]})
(comment
  (trombi/run simulation {:concurrency 4 :duration (Duration/ofSeconds 1)})
  )



















(def dynamic-simulation
  {:name "Huutopussi simulation"
   :scenarios [{:name "Player join"
                :allow-early-termination? true
                :step-fn (fn [{:keys [match-id match-status marked-as-ready? player-id] :as ctx}]
                           (cond
                             (nil? match-id) [{:name "Start match make"
                                               :request start-matchmake} ctx]
                             (not= "matched" match-status) [{:name "Get match status"
                                                             :sleep-before (constantly 100)
                                                             :request get-and-update-match-status} ctx]
                             (not marked-as-ready?) [{:name "Mark as ready to play"
                                                      :request mark-as-ready} ctx]
                             :else (do
                                     (log/info "Ending scenario for player id" player-id)
                                     nil)))}]})

(comment
  (trombi/run dynamic-simulation {:concurrency 4 :duration (Duration/ofSeconds 1)})



  (trombi/run dynamic-simulation {:rate 200 :duration (Duration/ofSeconds 15)})



  (trombi/run dynamic-simulation {:rate 500
                                  :duration (Duration/ofSeconds 20)
                                  :reporters [trombi-gatling-highcharts-reporter.core/reporter]}))
