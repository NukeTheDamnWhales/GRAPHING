(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async :refer [>! <! >!! <!! chan go close! sub pub go-loop alts!]]
    [buddy.sign.jwt :as jwt]
    [my-webapp.jwt :as auth]))

(defn check-queue-token [queue]
  (let [current-queue (-> @queue :users)]
          (into [] (map (fn [x] (try (jwt/unsign (get x 1) auth/secret)
                                    "pass"
                                    (catch Exception e
                                      (send queue update-in [:users] dissoc (get x 0))
                                      "fail"))))
                        current-queue)))

(defrecord UserQueue []

  component/Lifecycle

  (start [this]
    (let [in (chan 1)
          streamer (pub in :online-users)
          user-queue (agent {:users {}})
          kill-channel (chan 1)]

      (add-watch user-queue :user-queue (fn [_ _ _ n]
                                          (go (>! in {:online-users :user-listener :data n}))))
      (go-loop []
        (let [[n _] (alts!
                     [(async/timeout 1000) kill-channel])]
          (check-queue-token user-queue)
          (if (= n "kill")
            nil
            (recur))))
      (assoc this :queue {:streamer streamer :in in :user-queue user-queue :kill-channel kill-channel})))

  (stop [this]
    (prn this)
    (prn "hi")
    (when-let [{in :in user-queue :user-queue kill-channel :kill-channel} (:queue this)]
      (shutdown-agents)
      (prn "made it")
      (>!! kill-channel "kill")
      (prn "what??")
      (prn)
      (prn)
      (prn user-queue)
      (prn "made it here")
      (close! kill-channel)
      (prn kill-channel)
      (remove-watch user-queue :user-queue)
      (close! in)
      (close! kill-channel)
      (prn (:queue this)))
    (assoc this :queue nil)
    (prn this)))
