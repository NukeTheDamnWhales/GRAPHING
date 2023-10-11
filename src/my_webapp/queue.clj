(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async :refer [>! <! >!! <!! chan go close! sub pub go-loop]]
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

(defn user-queue-checker [queue]
  (go-loop []
    (<! (async/timeout 1000))
    (check-queue-token queue)
    (recur)))

(defrecord UserQueue []

  component/Lifecycle

  (start [this]
    (let [in (chan 1)
          streamer (pub in :online-users)
          user-queue (agent {:users {}})]

      (add-watch user-queue :user-queue (fn [_ _ _ n]
                                          (go (>! in {:online-users :user-listener :data n}))))
      (go-loop []
        (<! (async/timeout 1000))
        (check-queue-token user-queue)
        ;; (let [current-queue (-> @user-queue :users)]
        ;;   (doall (map (fn [x] (try (jwt/unsign (get x 1) auth/secret)
        ;;                          "pass"
        ;;                          (catch Exception e
        ;;                            (send user-queue update-in [:users] dissoc (get x 0))
        ;;                            "fail")))
        ;;             current-queue)))
        (recur))

      (assoc this :queue {:streamer streamer :in in :user-queue user-queue})))

  (stop [this]
    (when-let [{in :in user-queue :user-queue} (:queue (:queue this))]
      (close! in)
      (remove-watch user-queue :user-queue)
      (shutdown-agents))
    (assoc this :queue nil)
    (prn (:queue this))))
