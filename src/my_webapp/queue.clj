(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async :refer [>! <! >!! <!! chan go close!]]
    [buddy.sign.jwt :as jwt]
    [my-webapp.jwt :as auth]))

(defonce userqueue (agent {:users {}}))

(def queue (atom {:users {}}))

(defn user-queue []
  (let [in (chan)
        out (chan)]
    (go (<! in)
        (>! out "hot dog")
        [in out])))


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
    (let [[in out] [(chan 1) (chan)]]
      (go (>! in "what"))
      (assoc this :queue in))
              ;; (async/go-loop []
              ;;   (<! (async/timeout 1000))
              ;;   (async/put! in "hi")
              ;;   (recur))
              ;; (async/go-loop []
              ;;   (prn (<! in))
              ;;   (recur))
              ;;(assoc this :queue userqueue)
    )

  (stop [this]
    (when-let [in (:queue this)]
      (close! in))
    (prn (:queue this))
    (assoc this :queue nil)
    (send userqueue assoc-in [:users] {})))
