(ns my-webapp.auth
  (:require [io.pedestal.interceptor :refer [interceptor]]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]))

(def debugger-inter
  (interceptor
   {:name ::debug-inter
    :enter (fn [context]
             (let [selections (first (-> context :request :parsed-lacinia-query :selections))
                   fields (map #(:field-name %) (:selections selections))
                   query (-> selections
                             :field-definition
                             :field-name)]
               (prn selections)
               (prn)
               (prn query)
               (prn)
               (prn fields))
             context)}))


(defn load-auths
  [component]
  (-> (io/resource "my-auths.edn")
      slurp
      edn/read-string))

(defn is-own-user
  [db]
  (fn [context]
    ))



(defn resolver-map
  [component]
  (let [db (:auth db)]
    {:Unauthed/Query/GetUser/password (my-test-resolver db)}))

(defn check-auth
  [level query fields component]
  (let [perms (-> component
                  :auth
                  :level)
        fields (:fields (:query perms))
        conditions (map fields perms)]
    ))

(defrecord Auth []

  component/Lifecycle

  (start [this]
    (assoc this :auth (load-auths this)))

  (stop [this]
    (assoc this :auth nil)))
