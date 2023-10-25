(ns my-webapp.jwt
  (:require
   [cheshire.core :as cheshire]
   [buddy.sign.jwt :as jwt]
   [clj-time.core :as time]
   [clojure.core.async :as async :refer [put!]]
   [io.pedestal.interceptor :refer [interceptor]]
   [com.walmartlabs.lacinia.pedestal.internal :as internal]
   [my-webapp.db :as db]
   [clojure.string :as string]
   [clojure.pprint]))

(defonce token-time 10)
(defonce secret "abc")

(def access-level
  {:guest 0
   :regular 1
   :admin 2})

(defn token-verify
  [token]
  (if-let
      [verified token]
      ((-> verified :access-level keyword) access-level)
      0))

(defn auth-walk
  [selections]
  (mapcat (fn [x]
            (let [des ((juxt :field-definition :selections) x)]
              (concat (some-> des pop peek :directives peek :directive-args vals)
                      (some-> des peek auth-walk)))) selections))

(def introspection-interceptor
  (interceptor
   {:name ::introspection-inter
    :enter (fn [context]
             (let [{:keys [id response-data-ch body]} (:request context)]
               (when (not-empty
                    (re-seq
                     #"(?i)(__Schema|__typeName|__Type|__TypeKing|__Field|__InputValue|__EnumValue|__Directive)"
                     body))
                 (put! response-data-ch {:type :flag
                                         :id id
                                         :payload "on the right track"})))
             context)}))


(defn token-interceptor
  [queue]
  (interceptor
   {:name ::token-inter
    :enter (fn [context]
             (let [unsign (get-in context [:request :headers "authorization"])]
               (if-let [token (try (jwt/unsign unsign secret)
                                   (catch Exception e
                                     nil))]
                 ;; (send-off (:user-queue (:queue queue)) assoc-in [:users (keyword (:user token))] assoc unsign)
                 (assoc-in context [:request :token] token)
                 context)))
    :leave (fn [context]
             (prn "we are here hopefully???")
             (prn "yessir")
             context)}))

(def auth-interceptor
  (interceptor
   {:name ::debug-inter
    :enter (fn [context]
             (let [query-selections (get-in context [:request :parsed-lacinia-query :selections])
                   auth-map (map (fn [x] (if x
                                          (x access-level)
                                          0))
                                 (auth-walk query-selections))
                   users-auth (token-verify (-> context :request :token))]
               (if (every? true? (map (fn [x] (<= x users-auth)) auth-map))
                 context
                 (assoc context :response
                        (internal/failure-response
                         (internal/message-as-errors "Only true pickle aficionados may access this field"))))))}))

(defn create-claim
  [username db queue]
  (let [user-object (db/find-user-by-username db username)
        {access-level :accessLevel userid :id} user-object
        token (jwt/sign {:user username
                         :access-level access-level
                         :user-id userid
                         :exp (time/plus (time/now) (time/minutes 6))} secret)]
    (send-off (:user-queue (:queue queue)) assoc-in [:users (keyword username)] token)
    token))


(def cookie-response-interceptor
  "An interceptor that sees if the response body is a map and, if so,
  converts the map to JSON and sets the response Content-Type header."
  (interceptor
   {:name ::json-response
    :leave (fn [context]
             (let [setcookie? (get-in context [:response :setcookie?])]
               (-> context
                   (assoc-in [:response :headers "Set-Cookie"] "jwt=hithere; Secure; HttpOnly;"))))}))
