(ns my-webapp.system
  (:require [com.stuartsierra.component :as component]
            [my-webapp.schema :as schema]
            [my-webapp.server :as server]
            [my-webapp.db :as db]
            [my-webapp.queue :as us]
            [my-webapp.messages :as ms]))

(defn new-system
  []
  (assoc (component/system-map)
         :queue (us/map->UserQueue {})
         :messages (component/using
                    (ms/map->UserMessages {})
                    [:queue])
         :db (component/using
              (db/map->MyWebappDb {})
              [:queue])
         :server (component/using
                  (server/map->Server {})
                  [:schema-provider :db :queue])
         :schema-provider (component/using
                           (schema/map->SchemaProvider {})
                           [:db :queue :messages])))
