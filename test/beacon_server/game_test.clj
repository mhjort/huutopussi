(ns beacon-server.game-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.async :refer [chan <!! poll!]]
            [beacon-server.game :as game]))

(deftest play-card-returns-ok-and-pushes-to-input-channel-when-it-is-player-turn
  (let [input-channel (chan)
        matches (atom {"match-a" {:players [{:name "player-b" :input-channel input-channel}]
                                  :game-model {:next-player-id "player-b"
                                               :players {"player-b" {:player-id "player-b"
                                                                     :player-index 0
                                                                     :hand-cards ["J"]}}}}})]
    (is (= {:ok true} (game/play-card matches "match-a" "player-b" 0)))
    (is (= "J" (<!! input-channel)))))

(deftest play-card-returns-false-and-do-not-push-to-input-channel-when-it-is-not-player-turn
  (let [input-channel (chan)
        matches (atom {"match-a" {:players [{:name "player-b" :input-channel input-channel}]
                                  :game-model {:next-player-id "player-c"
                                               :players {"player-b" {:player-id "player-b"
                                                                     :hand-cards ["J"]}
                                                         "player-c" {:player-id "player-c"
                                                                     :hand-cards ["K"]}}}}})]
    (is (= {:ok false} (game/play-card matches "match-a" "player-b" 0)))
    (is (= nil (poll! input-channel)))))
