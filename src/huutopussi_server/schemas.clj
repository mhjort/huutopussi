(ns huutopussi-server.schemas
  (:require [malli.core :as m]))

(def Teams
  ;Note! Only exactly 2 teams with both having 2 players is supported
  [:map-of :keyword [:map
                     [:players [:repeat {:min 2 :max 2} string?]]
                     [:total-score {:optional true} :int]]])

(defn validate-schema [schema data]
  (when-not (m/validate schema data)
    (throw (ex-info "Malli validation failed" {:value data
                                               :error (m/explain schema data)}))))
