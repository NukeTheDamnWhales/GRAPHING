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
              [clojure.pprint]
              [my-webapp.flagencoding :as flag]))

;; Updated ones here and onwards

(defn user-by-username
  [db]
  (fn [context args _]
    ;; (prn (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")))
    (db/find-user-by-username db (:user (-> context :request :token)))))

(defn user-by-token
  [db]
  (fn [context _ _]
    (let [auth (-> context :request :token :user)]
      (if auth
        (schema/tag-with-type (db/find-user-by-username db auth) :User)
        (schema/tag-with-type {:message "error"} :NotFoundAcceptableNull)))))

(defn user-by-id
  [db]
  (fn [context args _]
    (db/find-user-by-id db (:id args))))

(defn post-by-id
  [db]
  (fn [context args _]
    (let [post (db/find-post-by-id db (:id args))
          board (-> post :board)
          user (-> context :request :token :user-id)
          board-members (mapv #(:id %) (db/find-user-by-board db board))
          owner (:owner-id (db/find-board-by-id db board))]
      (if (or (some #{user} board-members) (empty? board-members) (= user owner))
        post
        "error"))))

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

(defn create-board
  [db]
  (fn [context args _]
    (let [{:keys [title]} args]
      (if-let [user (-> context :request :token :user-id)]
        (db/create-board db user title)
        "error"))))


(defn create-post
  ;; insecure, check membership priv
  ;; Need to also make sure can create posts on public boards
  [db]
  (fn [context args _]
    (let [{;; postid :postId
           title :title
           text :body
           board :board} args
          user (-> context :request :token :user-id)
          board-members (mapv #(:id %) (db/find-user-by-board db board))
          owner (:owner (db/find-board-by-id db board))]
      ;;below will verify you are in members, just need to also allow
      ;;on public and on boards created by user
      ;;(some #{user} (mapv (fn [x] (:id x)) board-members))
      (if (or (some #{user} board-members) (empty? board-members) (= user owner))
        (db/create-post-for-user db user title text board)
        "error"))))

(defn refresh-token
  [db queue]
  (fn [context _ _]
    (try (auth/create-claim (:user (jwt/unsign (get-in (:request context) [:headers "authorization"]) auth/secret))
                        db queue)
         (catch Exception e
           "error"))))

(defn login
  [db queue]
  (fn [context args _]
    (let [{:keys [username password]} args
          actualpass (:password (db/find-user-by-username db username))
          ex-token-user (-> context :request :token :user)
          token (auth/create-claim username db queue)]
      (if (= password actualpass)
        (do
          (when (not (= ex-token-user username))
            (send (:user-queue (:queue queue)) update-in [:users] dissoc (keyword username)))
          (send (:user-queue (:queue queue)) assoc-in [:users (keyword username)] token)
          token)
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
    (let [{id :user-id} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) auth/secret)
                          (catch Exception e
                            {:user-id nil}))
          {:keys [body post parent]} args]
      (if id
        (db/create-comment db body post id parent)
        "error"))))

(defn delete-comment
  [db]
  (fn [context args _]
    (let [{user-id :user-id} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) auth/secret)
                                  (catch Exception e
                                    {:user-id nil}))
          {:keys [id]} args]
      (if (= user-id (:user (db/find-comment-by-id db id)))
        (db/delete-comment db id)
        "error"))))
;; (update-in context [:response :headers] (conj headers "Set-Cookie"
;;                                               (str (auth/create-claim username db) "; " "HttpOnly"),))
(defn log-out
  [queue messages]
  (fn [context _ _]
    (let [user (-> context :request :token :user)
          ;; {username :user} (try (jwt/unsign (get-in (:request context) [:headers "authorization"]) "abc")
          ;;                       (catch Exception e
          ;;                         {:user nil}))
          ]
      (if user
        (do
          (prn "we are fucked here")
          (a/put! (-> messages :messages :messages) {:killing user})
          (prn "we are less fucked and logged out")
          (send (:user-queue (:queue queue)) assoc-in [:users (keyword user)] nil)
          "Logged Out")
        "Not logged in"))))

(defn add-members
  [db queue]
  (fn [context args _]
    (let [user-id (-> context :request :token :user-id)
          {:keys [users board]} args
          selected-board-owner (:owner (db/find-board-by-id db board))]
      (db/add-users-to-board db users board queue)
      ;; (if (= user-id selected-board-owner)
      ;;   (db/add-users-to-board db users board queue)
      ;;   (mapv (fn [x] {:id x :user "could not add"}) users))
      )))

(defn send-message
  [messages]
  (fn [context args _]
    (let [to-send (:messages (:messages messages))
          message {:messages {:user (:user args)
                              :message (:message args)
                              :from (-> context :request :token :user)}}]
      (prn message)
      (go (>! to-send message))
      (:messages message))))

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
        (let
         [n (<! out)]
          (if (not n)
            (-> (list "no one here !") source-stream)
            (do (-> n :data :users keys source-stream)
                (recur)))))
      #(do
         (prn "Connection Closed")
         (unsub (:streamer (:queue queue)) :user-listener out)))))


(defn louise-api-call
  []
  (fn [context _ _]
    (let [token (get-in context [:request :headers "authorization"])]
      (if (= token "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJCcmlnZyBXaWdzIiwibmFtZSI6IkJyaWdnIFdpZ3MifQ.Dt9m5qzD83WGbbT13hSXtLH_AQnWtacXj8dPwQDLKok")
        "flag-{My_compressed_H3art_ACC3SS_GRANT#D**}"
        "error :'-("))))


(defn message-sub
  [messages queue]
  (fn [_ args source-stream]
    (let [send-own (:sources (:messages messages))
          return (chan 1)
          ;; not huge on this :/
          username (try (:user (jwt/unsign (:token args) auth/secret))
                        (catch Exception e
                          nil))]
      (when username
        (>!! send-own {:sources {:user username :channel return :token (:token args)}}))
      (go-loop []
        (let [test (<! return)
              {:keys [user from message count]} test
              to-return {:message message :user user :from from :count count}]
          (prn)
          (prn "RETURN VALUE" to-return)
          (prn)
          (if (nil? message)
            nil
            (do
              (-> to-return source-stream)
              ;; (if-let [his (:history to-return)]
              ;;   (-> his source-stream)
              ;;   (-> (list to-return) source-stream))
              (recur)))))
      #(prn "message sub closed")
           ;; (send (-> :queue :user-queue) assoc-in [:users (keyword username)] nil)
      )))

(defn flag-returner
  [_ _ _]
  {:anothersuprise "flag{WhyMustILearnGraphQL}"})

(defn enum-flag-returner
  [_ _ _]
  (schema/tag-with-type {:message "flag{WhatAStrangeSyntax}"} :flagenumobject))

(defn secure-pickle-channel
  [_ args source-stream]
  (let [message (:message args)
        encoded-flag (map #(flag/encode % [])
                          (flag/char-to-ascii-map "flag{EverybodyLovesMath}"))
        encoded (try (map #(flag/encode % []) (flag/char-to-ascii-map (peek message)))
                     (catch Exception e
                       nil))]
    (if encoded
      (do (-> encoded source-stream)
          (-> encoded-flag source-stream)
          (-> source-stream))
      (-> "couldnt encode string :(" source-stream))
    (-> nil source-stream))
  #(prn "finished secure pickle"))

(defn streamer-map
  [component]
  (let [queue (:queue component)
        messages (:messages component)]
    {:Subscription/LoggedInUsers (online-users queue)
     :Subscription/TheGodlyPickle (super-subscription)
     :Subscription/MessageSub (message-sub messages queue)
     :Subscription/SecurePickleChannel secure-pickle-channel
     }))


(defn resolver-map
  [component]
  (let [db (:db component)
        queue (:queue component)
        messages (:messages component)]
    {:Query/GetUser (user-by-id db)
     :Query/UserByUsername (user-by-username db)
     :Query/GetPost (post-by-id db)
     :Query/GetComment (comment-by-id db)
     :Query/GetAllBoards (get-all-boards db)
     :Query/PostsByBoard (posts-by-board db)
     :Query/CommentsByPost (post-to-comment db)
     :Query/UserByToken (user-by-token db)
     :Query/SuperSpecialAPICall (louise-api-call)
     :Mutation/CreateBoard (create-board db)
     :Mutation/CreatePost (create-post db)
     :Mutation/LogIn (login db queue)
     :Mutation/RefreshToken (refresh-token db queue)
     :Mutation/LogOut (log-out queue messages)
     :Mutation/CreateUser (create-user db)
     :Mutation/CreateComment (create-comment db)
     :Mutation/DeleteComment (delete-comment db)
     :Mutation/AddMembers (add-members db queue)
     :Mutation/SendMessage (send-message messages)
     :Board/members (board-to-user db)
     :Board/owner (board-to-owner db)
     :Board/specialsuprise flag-returner
     :Comment/post (comment-to-post db)
     :flagCURLYBRACKETNosyPickleEnthusiastCLOSINGCURLYBRACKET/howaboutme enum-flag-returner
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

(defrecord SchemaProvider [schema db queue messages]

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
