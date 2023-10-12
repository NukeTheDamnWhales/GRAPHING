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
              [clojure.core.async :as a :refer [go go-loop <! >! <!! >!! chan sub pub close! unsub]]
              [my-webapp.queue :as q]))

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
  (fn [context args _]
    (let [{;; postid :postId
           title :title
           text :body
           board :board} args
          user (try (:user-id (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc"))
                    (catch Exception e
                      nil))]
      (prn user title text board)
      (if user
        (db/create-post-for-user db user title text board)
        "you are not logged in :("))))

(defn refresh-token
  [db queue]
  (fn [context _ _]
    (let [{username :user}
          (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")]
      (auth/create-claim username db queue))))

(defn login
  [db queue]
  (fn [context args _]
    (let [{username :username
           password :password} args
          actualpass (:password (db/find-user-by-username db username))]
      (if (= password actualpass)
        (auth/create-claim username db queue)
        "error"))))

(defn create-user
  [db]
  (fn [_ args _]
    (let [{username :username
           password :password} args]
      (try (db/create-user db username password)
           (catch Exception e
             "Error")))))

;; (update-in context [:response :headers] (conj headers "Set-Cookie"
;;                                               (str (auth/create-claim username db) "; " "HttpOnly"),))
(defn log-out
  [queue]
  (fn [context _ _]
    (let [user (-> context :token :user)
          {username :user} (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")]
      (send (:user-queue (:queue queue)) assoc-in [:users (keyword username)] nil))
    "Logged Out"))


(defn subscription-test
  [queue]
  (fn [_ _ source-stream]
    (let [out (chan 1)
          init-queue (-> @(:user-queue (:queue queue)) :users keys)]
      (if init-queue
        (-> init-queue source-stream)
        (-> (list "no one here !") source-stream))
      (sub (:streamer (:queue queue)) :user-listener out)
      (go-loop []
        (let [to-push (-> (<! out) :data :users keys)]
          (if to-push
            (-> to-push source-stream)
            (-> (list "no one here !") source-stream)))
        (recur))
      #(do
         (prn "Connection Closed")
         (unsub (:streamer (:queue queue)) :user-listener out)))))

(defn streamer-map
  [component]
  (let [queue (:queue component)]
    {:Subscription/LoggedInUsers (subscription-test queue)}))


(defn resolver-map
  [component]
  (let [db (:db component)
        queue (:queue component)]
    {:Query/GetUser (user-by-id db)
     :Query/UserbyUsername (user-by-username db)
     :Query/GetPost (post-by-id db)
     :Query/GetComment (comment-by-id db)
     :Query/GetAllBoards (get-all-boards db)
     :Query/PostsByBoard (posts-by-board db)
     :Query/CommentsByPost (post-to-comment db)
     :Mutation/CreatePost (create-post db)
     :Mutation/LogIn (login db queue)
     :Mutation/RefreshToken (refresh-token db queue)
     :Mutation/LogOut (log-out queue)
     :Mutation/CreateUser (create-user db)
     :Comment/post (comment-to-post db)
     :Post/user (post-to-user db)}))

(defn load-schema
  [component]
  (-> (io/resource "my-schema.edn")
      slurp
      edn/read-string
      (util/inject-resolvers (resolver-map component))
      (util/inject-streamers (streamer-map component))
      ;; (schema/compile {:enable-introspection? false})
      schema/compile
      ))

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
