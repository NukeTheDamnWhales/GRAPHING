(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async]
    [buddy.sign.jwt :as jwt]
    [my-webapp.jwt :as auth]))

(defonce userqueue (agent {:users {}}))

(defn check-queue-token [user-map]
  (try (jwt/unsign (get user-map 1) auth/secret)
       "pass"
       (catch Exception e
         (send userqueue update-in [:users] dissoc (get user-map 0))
         "fail")))

(defn user-queue-checker []
  (async/go-loop []
    (async/<! (async/timeout 1000))
    (map check-queue-token (-> @userqueue :users))
    (prn @userqueue)
    (recur)))

(defrecord UserQueue []

  component/Lifecycle

  (start [this]
    (assoc this :queue userqueue))

  (stop [this]
    (assoc this :queue nil)
    (send userqueue assoc-in [:users] {})))
