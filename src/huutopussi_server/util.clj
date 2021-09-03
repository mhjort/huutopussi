(ns huutopussi-server.util
  (:require [clojure.pprint :refer [pprint]]))

(defn pretty-print [m]
  (with-out-str (pprint m)))
