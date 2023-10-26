(ns my-webapp.userflag
  (:require
    [com.stuartsierra.component :as component]
    [clojure.core.async :as async :refer [chan go-loop <! >! >!! timeout alts!]]
    [clojure.string]
    [my-webapp.flagencoding]))


(defrecord UserFlag [messages]

  component/Lifecycle

  (start [this]
    (let [kill (chan)]
      (go-loop []
        (let [[n _] (alts! [(timeout (* 60 1000)) kill])]
          (if (= "kill" n)
            nil
            (do
              (-> this :messages :messages) (>! (:messages (:messages (:messages this)))
                                                {:messages {:user (str "logger" (mapv #(my-webapp.flagencoding/encode % []) (my-webapp.flagencoding/char-to-ascii-map
                                                                                                                (get
                                                                                                                 (clojure.string/split
                                                                                                                  (str (java.time.LocalDateTime/now)) #":") 1))))
                                                            :message "flag{ThatWasOverlyComplicated}"
                                                            :from "system"}})
              (<! (timeout (* 1000 10)))
              (recur)))))
      (assoc this :userflag kill)))

  (stop [this]
    (>!! (:userflag this) "kill")
    (assoc this :userflag nil)))
