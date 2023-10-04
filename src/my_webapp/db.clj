(ns my-webapp.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [com.stuartsierra.component :as component])
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
                             :auth :auth
                             :created_at :createdAt
                             :updated_at :updatedAt}))

(defn- remap-auth
  [row-data]
  (set/rename-keys row-data {:auth_id :id
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
                             :post :post
                             :created_at :createdAt
                             :updated_at :updatedAt}))

(defn- remap-board
  [row-data]
  (set/rename-keys row-data {:board_id :id
                             :title :title}))

(defn create-post-for-user
  [component user title text]
  (jdbc/execute! component
                 ["insert into posts (title, body, user_id) values (?, ?, ?)" title text user])
  nil)

(defn find-post-by-comment
  [component post-id]
  (->> (jdbc/query component
                   ["select post_id, title, body, user_id, board_id, created_at, updated_at from posts where post_id = ?" post-id])
       (map remap-post)))

(defn find-comment-by-post
  [component post-id]
  (->> (jdbc/query component
               ["select comment_id, body, post, created_at, updated_at from comments where post = ?" post-id])
       (map remap-comment)))

;; Field Resolver Format below
(defn find-auth-by-user
  [component auth-id]
  (->> (jdbc/query component
                   ["select auth_id, loggedin, created_at, updated_at from auths where auth_id = ?" auth-id])
       (map remap-auth)))

(defn find-user-by-post
  [component user-id]
  (->> (jdbc/query component
               ["select user_id, name, username, password, accesslevel, email, auth, created_at, updated_at from users where user_id = ?" user-id])
       (map remap-user)))


(defn find-auth-by-id
  [component auth-id]
  (-> (jdbc/query component
                  ["select auth_id, loggedin, created_at, updated_at from auths where auth_id = ?" auth-id])
      first
      remap-auth
      ))

(defn find-post-by-id
  [component post-id]
  (-> (jdbc/query component
                  ["select post_id, title, body, user_id, board_id created_at, updated_at from posts where post_id = ?" post-id])
      first
      remap-post
      ))

(defn find-comment-by-id
  [component comment-id]
  (-> (jdbc/query component
                  ["select comment_id, body, post, created_at, updated_at from comments where comment_id = ?" comment-id])
      first
      remap-comment))

(defn find-user-by-id
  [component user-id]
  (-> (jdbc/query component
                  ["select user_id, name, username, password, accesslevel, email, loggedin, auth, created_at, updated_at from users where user_id = ?" user-id])
      first
      remap-user
      ))

(defn find-user-by-username
  [component user-name]
  (-> (jdbc/query component
                  ["select user_id, name, username, password, accesslevel, email, loggedin, auth, created_at, updated_at from users where username = ?" user-name])
      first
      remap-user
      ))

(defn get-all-boards
  [component]
  (map remap-board (jdbc/query component
              ["select * from boards"])))

(defn get-posts-by-board
  [component board-id]
  (map remap-post (jdbc/query component
               ["select * from posts where board_id = ?" board-id])))

(defrecord MyWebappDb [^ComboPooledDataSource datasource]

  component/Lifecycle

  (start [this]
    (assoc this :datasource (pooled-data-source "localhost" "mydb" "my_role" "lacinia" 25432)))

  (stop [this]
    (.close datasource)
    (assoc this :datasource nil)))
