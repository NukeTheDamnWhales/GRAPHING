(ns my-webapp.jwt
  (:require
   [cheshire.core :as cheshire]
   [buddy.sign.jwt :as jwt]
   [clj-time.core :as time]
   [io.pedestal.interceptor :refer [interceptor]]
   [com.walmartlabs.lacinia.pedestal.internal :as internal]
   [my-webapp.db :as db]))

(defonce token-time 10)
(defonce secret "abc")

(def access-level
  {:guest 0
   :regular 1
   :admin 2})

(defn token-verify
  [context]
  (if-let
      [token (jwt/unsign (get-in (:request context) [:headers "authorization"]) secret)]
    (do (assoc context :token token)
      (:access-level token))
    :guest))

(defn auth-walk*
  [auth-map]
  (flatten (map (fn [x] (if (:selections x)
                         (cons (-> x
                                   :field-definition
                                   :directives)
                               (auth-walk* (:selections x)))
                         (-> x
                             :field-definition
                             :directives))) auth-map)))

(defn auth-walk
  [auth-map]
  (map #(-> %
            :directive-args
            :role) (auth-walk* auth-map)))

(def token-interceptor
  (interceptor
   {:name ::token-inter
    :enter (fn [context]
             (let [token (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) secret)
                              (catch Exception e
                                nil))]
               (if token
                 (assoc context :token token)
                 context)))
    :leave (fn [context]
             context)}))

(defn auth-interceptor
  [db]
  (interceptor
   {:name ::debug-inter
    :enter (fn [context]
             (let [{{{query-selections
                      :selections}
                     :parsed-lacinia-query}
                    :request} context
                   auth-map
                   (map (fn [x] (if x
                                 (x access-level)
                                 0)) (auth-walk query-selections))
                   users-auth
                   (try
                     ((keyword (str (token-verify context))) access-level)
                     (catch Exception e
                       0))]
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
                         :exp (time/plus (time/now) (time/minutes 8))} secret)]
    (send (:user-queue (:queue queue)) update-in [:users] conj {(keyword username) token})
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
