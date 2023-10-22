(ns my-webapp.jwt
  (:require
   [cheshire.core :as cheshire]
   [buddy.sign.jwt :as jwt]
   [clj-time.core :as time]
   [io.pedestal.interceptor :refer [interceptor]]
   [com.walmartlabs.lacinia.pedestal.internal :as internal]
   [my-webapp.db :as db]
   [clojure.pprint]))

(defonce token-time 10)
(defonce secret "abc")

(def access-level
  {:guest 0
   :regular 1
   :admin 2})

(defn token-verify
  [context]
  (if-let
      [token (:token context)]
    (keyword (:access-level token))
    :guest))

;; (defn auth-walk2*
;;   [selections]
;;   (mapv #(reduce-kv (fn [m k v]
;;                       (into m
;;                             (cond
;;                               (= :selections k) (auth-walk2* v)
;;                               (= :field-definition k) (mapv (fn [x] (-> x :directive-args :role))
;;                                                             (-> v :directives))))) [] %) selections))

;; (defn auth-walk2*
;;   [selections]
;;   (prn (reduce (partial identity) selections))
;;   (clojure.pprint/pprint ((juxt #(when-let [sel (:selections %)]
;;                                    (auth-walk2* sel))
;;                                 ;; (clojure.pprint/pprint %)(prn)
;;                                 #(some-> % :field-definition :directives)) (pop selections))))

(defn auth-walk2*
  [selections]
  (mapcat (fn [x]
            (let [des ((juxt :field-definition :selections) x)]
              (concat (some-> des pop peek :directives peek :directive-args vals)
                      (some-> des peek auth-walk2*)))) selections))

(defn auth-walk*
  [auth-map]
  (let [directives #(get-in % [:field-definition :directives])]
    (flatten (map (fn [x] (if (:selections x)
                           (cons (directives x)
                                 (auth-walk* (:selections x)))
                           (directives x))) auth-map))))

(defn auth-walk
  [auth-map]
  (map #(-> %
            :directive-args
            :role) (auth-walk* auth-map)))

(def token-interceptor
  (interceptor
   {:name ::token-inter
    :enter (fn [context]
             (if-let [token (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) secret)
                              (catch Exception e
                                nil))]
               (assoc context :token token)
                 context))}))

(defn auth-interceptor
  [db]
  (interceptor
   {:name ::debug-inter
    :enter (fn [context]
             (let [query-selections (get-in context [:request :parsed-lacinia-query :selections])
                   auth-map (map (fn [x] (if x
                                           (x access-level)
                                           0))
                                 (auth-walk2* query-selections))
                   users-auth ((token-verify context) access-level)]
               (clojure.pprint/pprint (auth-walk2* (-> context :request :parsed-lacinia-query :selections)))
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
    (send-off (:user-queue (:queue queue)) update-in [:users] conj {(keyword username) token})
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
