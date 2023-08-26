(ns my-webapp.core
  (:gen-class)
  (:require
   [com.stuartsierra.component :as component]
   [my-webapp.system :as system]))


(defonce system (system/new-system))

(defn start
  []
  (alter-var-root #'system component/start-system)
  :started)

(defn -main []
  (start))
