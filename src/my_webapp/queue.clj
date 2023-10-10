(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async :refer [>! <! >!! <!! chan go close! sub pub]]
    [buddy.sign.jwt :as jwt]
    [my-webapp.jwt :as auth]))

;; (defn check-queue-token [user-map]
;;   (try (jwt/unsign (get user-map 1) auth/secret)
;;        "pass"
;;        (catch Exception e
;;          (send userqueue update-in [:users] dissoc (get user-map 0))
;;          "fail")))

;; (defn user-queue-checker []
;;   (async/go-loop []
;;     (async/<! (async/timeout 1000))
;;     (map check-queue-token (-> @userqueue :users))
;;     (prn @userqueue)
;;     (recur)))

(defrecord UserQueue []

  component/Lifecycle

  (start [this]
    (let [in (chan 1)
          streamer (pub in :online-users)
          user-queue (agent {:users {}})]
      (add-watch user-queue :user-queue (fn [_ _ _ n]
                                          (go (>! in {:online-users :user-listener :data n}))))
      (assoc this :queue {:streamer streamer :in in :user-queue user-queue}))
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
    (when-let [{in :in user-queue :user-queue} (:queue (:queue this))]
      (close! in)
      (remove-watch user-queue :user-queue)
      (shutdown-agents))
    (prn (:queue this))
    (assoc this :queue nil)))
