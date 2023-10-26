(ns my-webapp.messages
  (:require
   [com.stuartsierra.component :as component]
   [clojure.core.async :as async :refer [>! <! >!! <!! chan go close! sub pub go-loop alt! alts! put!]]
   [clojure.pprint]))

(defrecord UserMessages [queue]

  component/Lifecycle

  (start [this]
    (let
     [message-map {:messages (chan 1) :sources (chan 1) :kill (chan 1)}]
      (map #(>!! (-> % vals :channel) "kill")
           (go-loop [{:keys [messages sources kill]} message-map
                     users {}]
             (prn)
             (clojure.pprint/pprint users)
             (prn)
             (let [[n _]
                   (alts!
                    [messages sources kill])]
               (prn n)
               (if (not (coll? n))
                 users
                 (recur
                  message-map
                  (cond
                    (= :sources (-> n keys first))
                    (if-let [c (-> n :sources :channel)]
                      (let [mes-back (-> n :sources :user keyword users :messages)
                            sent-m (into []
                                         (remove (fn [m]
                                                   (prn "WE ARE BLOCKING HERE at history resend sending: Sending to channel" c)
                                                   (prn "User is :" m)
                                                   (prn)
                                                   (let [{:keys [message user from counter]} m]
                                                     (put! c {:user user :message message :from from :count counter}))))
                                         mes-back)
                            his (-> n :sources :user keyword users :history)]
                        ;; (send-off (:user-queue (:queue queue)) assoc-in [:users (-> n :sources :user keyword)] (-> n :sources :token))
                        (when-let [x (into [] (remove nil? his))]
                          (when (not-empty x)
                            (try (into [] (map (fn [y] (prn y)
                                                 (put! c y))) x)
                                 (catch Exception e
                                   (prn "something went wrong here"))))
                          (prn "Succesfully sent history"))
                        (assoc-in users [(keyword (-> n :sources :user))]
                                  {:user (-> n :sources :user)
                                   :messages sent-m
                                   :channel c
                                   :history (if (empty? sent-m)
                                              his
                                              (conj his (mapv #(conj (-> n :sources :user keyword users :history) %) sent-m)))}))
                      ;; THIS LOOP DID STUFF

                      ;; (if-let [us (-> n :sources :user)]
                      ;; (update-in users [(keyword us)] assoc :channel nil)
                      users)
                    ;; )
                    (= :messages (-> n keys first))
                    (let [user-index (keyword (-> n :messages :user))
                          own-index (keyword (-> n :messages :from))]
                      ;; THIS IS BLOCKING FIX IT OR SOMETHING
                      (prn "WE ARE BLOCKING HERE: " user-index)
                      (if (and
                           (some-> n :messages :user keyword users :channel (>! (-> n :messages)))
                           (not-empty (-> n :messages)))
                        (do
                          (prn "made it 1: " n)
                          (update-in (update-in users [user-index :history]
                                                conj
                                                (assoc-in (-> n :messages) [:count]
                                                          (inc (count (-> user-index users :history))))) [own-index :history]
                                     conj
                                     (assoc-in (-> n :messages) [:count]
                                               (inc (count (-> own-index users :history))))))
                        (do
                          (prn "made it 2: " n)
                          (update-in users [user-index :messages]
                                     conj
                                     (-> n :messages)))))
                    :else
                    (do
                      (prn "we are removing the channel at" (-> n :killing keyword))
                      (prn)
                      (prn (assoc-in users [(-> n :killing keyword) :channel] nil))
                      ;; (-> n :killing keyword users :channel (>! "kill"))
                      (assoc-in users [(-> n :killing keyword) :channel] nil))))))))
      (assoc this :messages message-map)))

  (stop [this]
    (>!! (-> this :messages :kill) "kill")
    (close! (-> this :messages :messages))
    (close! (-> this :messages :sources))
    (close! (-> this :messages :kill))
    (assoc this :messages nil)))
