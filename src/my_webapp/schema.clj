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
              [my-webapp.queue :as q]
              [clojure.pprint]))

;; Updated ones here and onwards

(defn user-by-username
  [db]
  (fn [context args _]
    ;; (prn (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")))
    (db/find-user-by-username db (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")))))

(defn user-by-token
  [db]
  (fn [context _ _]
    (let [auth (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
                    (catch Exception e
                      "error"))
          username (:user auth)]
      (if username
        (schema/tag-with-type (db/find-user-by-username db username) :User)
        (schema/tag-with-type {:message "error"} :NotFoundAcceptableNull)))))

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
  (fn [_ _ post_id]
    (db/find-comment-by-post db (:id post_id))))

(defn post-to-user
  [db]
  (fn [_ _ user]
    (db/find-user-by-post db (:user user))))

(defn board-to-user
  [db]
  (fn [_ _ id]
    (db/find-user-by-board db (:id id))))

(defn board-to-owner
  [db]
  (fn [_ _ owner]
    (db/find-owner-by-board db (:owner owner))))

(defn comment-to-user
  [db]
  (fn [_ _ user]
    (db/find-user-by-comment db (:user user))))

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
      (if user
        (db/create-post-for-user db user title text board)
        "error"))))

(defn refresh-token
  [db queue]
  (fn [context _ _]
    (try (auth/create-claim (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc"))
                        db queue)
         (catch Exception e
           "error"))))

(defn login
  [db queue]
  (fn [context args _]
    (let [{:keys [username password]} args
          actualpass (:password (db/find-user-by-username db username))
          existing (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
                        (catch Exception e
                          nil))]
      (if (= password actualpass)
        (do (when existing
              (send-off (:user-queue (:queue queue)) assoc-in [:users (keyword (:user existing))] nil))
            (auth/create-claim username db queue))
        "error"))))

(defn create-user
  [db]
  (fn [_ args _]
    (let [{username :username
           password :password} args]
      (try (db/create-user db username password)
           (catch Exception e
             "error")))))

(defn create-comment
  [db]
  (fn [context args _]
    (let [{id :user-id} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
                          (catch Exception e
                            {:user-id nil}))
          {:keys [body post parent]} args]
      (if id
        (db/create-comment db body post id parent)
        "error"))))

(defn delete-comment
  [db]
  (fn [context args _]
    (let [{user-id :user-id} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
                                  (catch Exception e
                                    {:user-id nil}))
          {:keys [id]} args]
      (if (= user-id (:user (db/find-comment-by-id db id)))
        (db/delete-comment db id)
        "error"))))
;; (update-in context [:response :headers] (conj headers "Set-Cookie"
;;                                               (str (auth/create-claim username db) "; " "HttpOnly"),))
(defn log-out
  [queue]
  (fn [context _ _]
    (let [user (-> context :token :user)
          {username :user} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
                                (catch Exception e
                                  {:user nil}))]
      (if username
        (do (send-off (:user-queue (:queue queue)) assoc-in [:users (keyword username)] nil)
            "Logged Out")
        "Not logged in"))))

(defn add-members
  [db queue]
  (fn [context args _]
    (let [{user-id :user-id} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
                  (catch Exception e
                    {:user-id nil}))
          {:keys [users board]} args
          selected-board-owner (:owner (db/find-board-by-id db board))]
      (if (= user-id selected-board-owner)
        (db/add-users-to-board db users board queue)
        "error")
      )))

(defn super-subscription
  []
  (fn [_ _ source-stream]
    (-> "hi!" source-stream)))

(defn online-users
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
    {:Subscription/LoggedInUsers (online-users queue)
     :Subscription/TheGodlyPickle (super-subscription)
     }))


(defn resolver-map
  [component]
  (let [db (:db component)
        queue (:queue component)]
    {:Query/GetUser (user-by-id db)
     :Query/UserByUsername (user-by-username db)
     :Query/GetPost (post-by-id db)
     :Query/GetComment (comment-by-id db)
     :Query/GetAllBoards (get-all-boards db)
     :Query/PostsByBoard (posts-by-board db)
     :Query/CommentsByPost (post-to-comment db)
     :Query/UserByToken (user-by-token db)
     :Mutation/CreatePost (create-post db)
     :Mutation/LogIn (login db queue)
     :Mutation/RefreshToken (refresh-token db queue)
     :Mutation/LogOut (log-out queue)
     :Mutation/CreateUser (create-user db)
     :Mutation/CreateComment (create-comment db)
     :Mutation/DeleteComment (delete-comment db)
     :Mutation/AddMembers (add-members db queue)
     :Board/members (board-to-user db)
     :Board/owner (board-to-owner db)
     :Comment/post (comment-to-post db)
     :Post/user (post-to-user db)
     :Post/comments (post-to-comment db)
     :Comment/user (comment-to-user db)
     }))

(defn load-schema
  [component]
  (let [uncompiled (-> (io/resource "my-schema.edn")
                       slurp
                       edn/read-string
                       (util/inject-resolvers (resolver-map component))
                       (util/inject-streamers (streamer-map component))
             ;; (schema/compile {:enable-introspection? false})
                       schema/compile
                       )]
    ;; {:normal (schema/compile uncompiled {:enable-introspection? false}) "ws" (schema/compile uncompiled)}
    uncompiled))

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
