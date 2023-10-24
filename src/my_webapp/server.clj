(ns my-webapp.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [io.pedestal.http :as http]
            [my-webapp.jwt :as auth]
            [io.pedestal.interceptor :refer [interceptor]]
            [com.walmartlabs.lacinia.pedestal.internal :as internal]
            [com.walmartlabs.lacinia.executor :as executor]))

(def ^:private default-api-path "/api")
(def ^:private default-asset-path "/assets/graphiql")
(def ^:private default-subscriptions-path "/ws")
(def ^:private default-host-address "localhost")


(defn default-interceptors
  ([compiled-schema]
   (default-interceptors compiled-schema nil))
  ([compiled-schema app-context]
   (default-interceptors compiled-schema app-context nil))
  ([compiled-schema app-context options]
   (default-interceptors compiled-schema app-context options nil))
  ([compiled-schema app-context options db]
   (default-interceptors compiled-schema app-context options db nil))
  ([compiled-schema app-context options db queue]
   [ ;; lp/initialize-tracing-interceptor
    ;;    auth/cookie-response-interceptor
    lp/json-response-interceptor
    lp/error-response-interceptor
    lp/body-data-interceptor
    (auth/token-interceptor queue)
    lp/graphql-data-interceptor
    ;;    permissions-check
    lp/status-conversion-interceptor
    lp/missing-query-interceptor
    (lp/query-parser-interceptor compiled-schema (:parsed-query-cache options))
    ;;    lp/disallow-subscriptions-interceptor
    lp/prepare-query-interceptor
    (lp/inject-app-context-interceptor app-context)
    auth/auth-interceptor
    auth/introspection-interceptor
    ;; lp/enable-tracing-interceptor
    lp/query-executor-handler]))

(defn my-service
  [compiled-schema db queue options]
  (let [{:keys [api-path ide-path asset-path app-context port host]
         :or {api-path default-api-path
              ide-path "/ide"
              asset-path default-asset-path
              port 8888
              host default-host-address}} options
        interceptors (default-interceptors compiled-schema app-context options db queue)
        routes (into #{[api-path :post interceptors :route-name ::graphql-api]
                       [ide-path :get (lp/graphiql-ide-handler options) :route-name ::graphiql-ide]}
                     (lp/graphiql-asset-routes asset-path))]
    (-> {:env :dev
         ::http/routes routes
         ::http/port port
         ::http/host host
         ::http/type :jetty
         ::http/join? false}
        lp/enable-graphiql
        (lp/enable-subscriptions compiled-schema options))))

(defrecord Server [schema-provider server queue]

  component/Lifecycle

  (start [this]
    (assoc this :server (-> schema-provider
                            :schema
                            (my-service (:db this) (:queue this) nil)
                            (merge {:env :dev
                                    ::http/allowed-origins {:creds true :allowed-origins (constantly true)}
                                    ::http/secure-headers {:content-security-policy-settings {:object-sec "'none'"}}})
                            http/create-server
                            http/start)))

  (stop [this]
    (http/stop server)
    (assoc this :server nil)))
