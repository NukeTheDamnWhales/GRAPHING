(ns my-webapp.schema
    (:require [clojure.java.io :as io]
              [com.stuartsierra.component :as component]
              [com.walmartlabs.lacinia.util :as util]
              [com.walmartlabs.lacinia.schema :as schema]
              [com.walmartlabs.lacinia.executor :as executor]
              [my-webapp.db :as db]
              [clojure.edn :as edn]
              [my-webapp.jwt :as auth]
              [buddy.sign.jwt :as jwt]
              [clojure.core.async :as a]))

;; Updated ones here and onwards

(defn user-by-username
  [db]
  (fn [context args _]
    ;; (prn (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")))
    (db/find-user-by-username db (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")))))

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

(defn user-by-id
  [db]
  (fn [context args _]
    (db/find-user-by-id db (:id args))))

(defn post-by-id
  [db]
  (fn [_ args _]
    (db/find-post-by-id db (:id args))))

(defn comment-by-id
  [db]
  (fn [_ args _]
    (db/find-comment-by-id db (:id args))))

(defn auth-by-id
  [db]
  (fn [_ args _]
    (db/find-auth-by-id db (:id args))))

(defn user-to-auth
  [db]
  (fn [_ _ auth]
    (db/find-auth-by-user db (:id auth))))

(defn post-to-comment
  [db]
  (fn [_ args _]
    (db/find-comment-by-post db (:post args))))

(defn post-to-user
  [db]
  (fn [_ _ user]
    (db/find-user-by-post db (:user user))))

(defn get-all-boards
  [db]
  (fn [_ _ _]
    (db/get-all-boards db)))

(defn posts-by-board
  [db]
  (fn [_ args _]
    (db/get-posts-by-board db (:board args))))

;; THIS WILL WORK DO THIS
(defn comment-to-post
  [db]
  (fn [_ _ post]
    (db/find-post-by-comment db (:post post))))


(defn create-post
  ;; insecure
  [db]
  (fn [_ args _]
    (let [{;; postid :postId
           user :userId
           title :title
           text :body} args]
      (db/create-post-for-user db user title text))))

(defn refresh-token
  [db]
  (fn [context _ _]
    (let [username (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc"))]
      (auth/create-claim username db))))

(defn login
  [db queue]
  (fn [context args _]
    (let [{username :username
           password :password} args
          actualpass (:password (db/find-user-by-username db username))]
      (prn (:response context))
      (if (= password actualpass)
        (do
          (auth/create-claim username db)
          (send (:queue queue) assoc :users username))
        "error"))))

        ;; (update-in context [:response :headers] (conj headers "Set-Cookie"
        ;;                                               (str (auth/create-claim username db) "; " "HttpOnly"),))

(defn subscription-test
  [db queue]
  (fn [x y z]
    (send (:queue queue) assoc :users "test")
    (-> @(:queue queue) z)))

(defn streamer-map
  [component]
  (let [db (:db component)
        queue (:queue component)]
    {:Subscription/Test (subscription-test db queue)}))


(defn resolver-map
  [component]
  (let [db (:db component)
        queue (:queue component)]
    {:Query/GetUser (user-by-id db)
     :Query/UserbyUsername (user-by-username db)
     :Query/GetPost (post-by-id db)
     :Query/GetComment (comment-by-id db)
     :Query/GetAuth (auth-by-id db)
     :Query/GetAllBoards (get-all-boards db)
     :Query/PostsByBoard (posts-by-board db)
     :Query/CommentsByPost (post-to-comment db)
     :Mutation/CreatePost (create-post db)
     :Mutation/LogIn (login db queue)
     :Mutation/RefreshToken (refresh-token db)
     :Comment/post (comment-to-post db)
     :User/auth (user-to-auth db)
     :Post/user (post-to-user db)}))

(defn load-schema
  [component]
  (-> (io/resource "my-schema.edn")
      slurp
      edn/read-string
      (util/inject-resolvers (resolver-map component))
      (util/inject-streamers (streamer-map component))
      schema/compile))

(defrecord SchemaProvider [db schema queue]

  component/Lifecycle

  (start [this]
    (assoc this :schema (load-schema this)))

  (stop [this]
    (assoc this :schema nil)))


(defn new-schema-provider
  []
  {:schema-provider (-> {}
                        map->SchemaProvider
                        (component/using [:db]))})
