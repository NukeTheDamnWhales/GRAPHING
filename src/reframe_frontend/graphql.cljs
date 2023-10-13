(ns reframe-frontend.graphql
  (:require
   [re-graph.core :as re-graph]
   [re-frame.core :as re-frame]
   [reframe-frontend.events :as events]))

(def GetAllBoards
  [::re-graph/query
   {:instance-id :a
    :id :GetAllBoards
    :query "{GetAllBoards {title id}}"
    :callback [::events/get-gql-boards]}])

(defn PostsByBoard [x]
  [::re-graph/query
   {:instance-id :a
    :id :PostsByBoard
    :query "($board: Int) {PostsByBoard(board: $board) {title id}}"
    :variables {:board (js/Number x)}
    :callback [::events/get-gql-board]}])

(defn GetPost [x]
  [::re-graph/query
   {:instance-id :a
    :id :GetPost
    :query "($id: Int) {GetPost(id: $id) {title body user {userName}}}"
    :variables {:id (js/Number x)}
    :callback [::events/get-gql-post]}])

(defn LogIn [x y]
  [::re-graph/mutate
   {:instance-id :a
    :query "($username: String $password: String) {LogIn(username: $username password: $password)}"
    :variables {:username x :password y}
    :callback [::events/handle-auth]}])

(def OnlineUsers
  [::re-graph/subscribe
   {:instance-id :b
    :id :LoggedInUsers
    :query "{LoggedInUsers}"
    :callback [::events/logged-in-users]}])


(def RefreshToken
  [::re-graph/mutate
   {:instance-id :a
    :query "{RefreshToken}"
    :callback [::events/handle-auth]}])

(def LogOut
  [::re-graph/mutate
   {:instance-id :a
    :query "{LogOut}"
    :callback [::events/log-out]}])

(defn CreatePost
  [x y z]
  [::re-graph/mutate
   {:instance-id :a
    :query "($title: String $body: String $board: Int) {CreatePost(title: $title body: $body board: $board)}"
    :variables {:title x :body y :board z}
    :callback [::events/do-nothing]}])

(defn CreateUser
  [x y]
  [::re-graph/mutate
   {:instance-id :a
    :query "($username: String $password: String) {CreateUser(username: $username password: $password)}"
    :variables {:username x :password y}
    :callback [::events/do-nothing]}])
