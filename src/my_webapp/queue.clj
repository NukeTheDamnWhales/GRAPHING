(ns my-webapp.queue
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async :refer [>! <! >!! <!! chan go close! sub pub go-loop alts! put!]]
    [buddy.sign.jwt :as jwt]
    [my-webapp.jwt :as auth]))

(defn check-queue-token [queue]
  (let [current-queue (-> @queue :users)]
    (into [] (map (fn [x] (try (jwt/unsign (get x 1) auth/secret)
                              "pass"
                              (catch Exception e
                                (send-off queue update-in [:users] dissoc (get x 0))
                                "fail"))))
          current-queue)))

;; (defn queue-checker [queue kill]
;;   (when (go (= "kill" (alts! [async/timeout 1000] kill)))))


(defrecord UserQueue []

  component/Lifecycle

  (start [this]
    (let [in (chan 1)
          using (chan 1)
          streamer (pub in :online-users)
          user-queue (agent {:users {}})
          kill-channel (chan 1)]

      (add-watch user-queue :user-queue (fn [_ _ _ n]
                                          (put! in {:online-users :user-listener :data n})))
      (>!! using "1")
      (go-loop []
        ;;(prn (. System (nanoTime)))
        (let [[n _] (alts!
                     [(async/timeout 2000) kill-channel])]
          (check-queue-token user-queue)
          (if (= n "kill")
            nil
            (recur))))

      (assoc this :queue {:streamer streamer :in in :user-queue user-queue :kill-channel kill-channel :using using})))

  (stop [this]
    (when-let [{in :in user-queue :user-queue kill-channel :kill-channel using :using} (:queue this)]
      (>!! kill-channel "kill")
      (close! using)
      (close! kill-channel)
      (remove-watch user-queue :user-queue)
      (close! in)
      (send user-queue assoc-in [:users] {}))
    (assoc this :queue nil)))
