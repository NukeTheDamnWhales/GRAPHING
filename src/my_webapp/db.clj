(ns my-webapp.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clojure.core.async :as async :refer [<! >! <!! >!! go put! close! chan]]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.schema :as schema])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn- pooled-data-source
  [host dbname user password port]
  (doto (ComboPooledDataSource.)
    (.setDriverClass "org.postgresql.Driver")
    (.setJdbcUrl (str "jdbc:postgresql://" host ":" port "/" dbname))
    (.setUser user)
    (.setPassword password)))

(defn- remap-user
  [row-data]
  (set/rename-keys row-data {:user_id :id
                             :name :fullName
                             :username :userName
                             :password :password
                             :accesslevel :accessLevel
                             :email :email
                             :loggedin :loggedIn
                             :created_at :createdAt
                             :updated_at :updatedAt}))

(defn- remap-post
  [row-data]
  (set/rename-keys row-data {:post_id :id
                             :title :title
                             :body :body
                             :created_at :createdAt
                             :updated_at :updatedAt
                             :user_id :user
                             :board_id :board}))

(defn- remap-comment
  [row-data]
  (set/rename-keys row-data {:comment_id :id
                             :body :body
                             :post_id :post
                             :user_id :user
                             :parent_id :parent
                             :created_at :createdAt
                             :updated_at :updatedAt}))

(defn- remap-members
  [row-data]
  (set/rename-keys row-data {:board_id :board
                             :user_id :id
                             :member_id :member}))

(defn- remap-board
  [row-data]
  (set/rename-keys row-data {:board_id :id
                             :user_id :user_id
                             :title :title}))

(defn create-user
  [component username password]
  (jdbc/execute! component
                 ["insert into users (username, accesslevel, password, name) values (?, ?, ?, ?)" username "regular" password "jeff"])
  nil)

(defn create-board
  [component user title]
  (jdbc/execute! component
                 ["insert into boards (owner, title) values (?, ?)" user title])
  nil)

(defn create-post-for-user
  [component user title text board]
  (jdbc/execute! component
                 ["insert into posts (title, body, user_id, board_id) values (?, ?, ?, ?)" title text user board])
  nil)

(defn find-post-by-comment
  [component post-id]
  (->> (jdbc/query component
                   ["select post_id, title, body, user_id, board_id, created_at, updated_at from posts where post_id = ?" post-id])
       (map remap-post)))

(defn find-comment-by-post
  [component post-id]
  (->> (jdbc/query component
                   ["select comment_id, body, user_id, post_id, parent_id, created_at, updated_at from comments where post_id = ?" post-id])
       (map remap-comment)))


(defn create-comment
  [component body post user parent_id]
  (->
   (jdbc/insert! component :comments
                 {:user_id user :body body :post_id post :parent_id parent_id})
   first
   remap-comment))

(defn delete-comment
  [component comment_id]
  (try (if (= 1 (first
             (jdbc/delete! component :comments
                           ["comment_id = ?" comment_id])))
     comment_id
     nil)
       (catch Exception e
         nil)))
;; Field Resolver Format below

(defn find-user-by-post
  [component user-id]
  (->> (jdbc/query component
                   ["select user_id, name, username, password, accesslevel, created_at, updated_at from users where user_id = ?" user-id])
       (map remap-user)))

(defn find-user-by-comment
  [component user-id]
  (->> (jdbc/query component
                   ["select user_id, name, username, password, accesslevel, created_at, updated_at from users where user_id = ?" user-id])
       (map remap-user)))


(defn find-post-by-id
  [component post-id]
  (-> (jdbc/query component
                  ["select * from posts where post_id = ?" post-id])
      first
      remap-post))

(defn find-board-by-id
  [component board-id]
  (-> (jdbc/query component
                  ["select * from boards where board_id = ?" board-id])
      first
      remap-board
      ))

(defn find-comment-by-id
  [component comment-id]
  (-> (jdbc/query component
                  ["select comment_id, body, post_id, user_id, parent_id, created_at, updated_at from comments where comment_id = ?" comment-id])
      first
      remap-comment))

(defn find-user-by-id
  [component user-id]
  (-> (jdbc/query component
                  ["select user_id, name, username, password, accesslevel, created_at, updated_at from users where user_id = ?" user-id])
      first
      remap-user
      ))

(defn find-user-by-username
  [component user-name]
  (-> (jdbc/query component
                  ["select user_id, name, username, password, accesslevel, created_at, updated_at from users where username = ?" user-name])
      first
      remap-user
      ))

(defn find-user-by-board
  [component auth-id]
  (let [users (map :user_id (jdbc/query component
                                        ["select user_id from members where board_id = ?" auth-id]))]
    (map
     (fn [x] (-> x first remap-user))
     (map (fn [x] (jdbc/query component ["select user_id, username from users where user_id =?" x]))
          users))))

(defn find-owner-by-board
  [component user_id]
  (-> (jdbc/query component
                  ["select user_id, username, accesslevel from users where user_id = ?" user_id])
      first
      remap-user))


(defn add-users-to-board
  [component users board_id queue]
  (go (<! (-> queue :queue :using))
      (let [user-records
            (mapv (fn [x] (:user_id x))
                  (jdbc/query component ["select user_id from members where board_id = ?" board_id]))
            dedup (filterv (fn [x] (not (some #{x} user-records))) users)]
        (if (not-empty dedup)
          (let [ret (try
                      (jdbc/insert-multi! component :members
                                          (mapv (fn [x] {:user_id x :board_id board_id}) dedup))
                      (catch Exception e
                        "error"))]
            (>! (-> queue :queue :using) 1)
            (if (coll? ret)
              (mapv remap-user ret)
              ret))
          (do
            (>! (-> queue :queue :using) 1)
            (schema/tag-with-type (list "duplicate or not authorized") :User))))))


(defn get-all-boards
  [component]
  ;; (prn (jdbc/query component
  ;;                  ["select * from boards"]))
  (map remap-board (jdbc/query component
                               ["select * from boards"])))

(defn get-posts-by-board
  [component board-id]
  (map remap-post (jdbc/query component
               ["select * from posts where board_id = ?" board-id])))


(defrecord MyWebappDb [^ComboPooledDataSource datasource queue]

  component/Lifecycle

  (start [this]
    (assoc this :datasource (pooled-data-source "127.0.0.1" "mydb" "my_role" "lacinia" 25432)))

  (stop [this]
    (<!! (:using (:queue queue)))
    (.close datasource)
    (assoc this :datasource nil)))
