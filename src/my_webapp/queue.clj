(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]))

(defonce userqueue (agent {}))

(defrecord UserQueue []

  component/Lifecycle

  (start [this]
    (assoc this :queue userqueue))

  (stop [this]
    (assoc this :queue nil)))
