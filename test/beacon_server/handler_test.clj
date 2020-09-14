(ns beacon-server.handler-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.mock.request :as mock]
            [beacon-server.handler :refer [app]]))

(deftest test-app
  (testing "index-html redirect"
    (let [response (app (mock/request :get "/"))]
      (is (= 302 (:status response))
      (is (= "" (:body response) "")))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
