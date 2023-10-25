(ns reframe-frontend.graphql
  (:require
   [re-graph.core :as re-graph]
   [re-frame.core :as re-frame]
   [reframe-frontend.events :as events]))

(def GetAllBoards
  [::re-graph/query
   {:instance-id :a
    :id :GetAllBoards
    :query "{GetAllBoards {title id owner {userName id} members {userName}}}"
    :callback [::events/get-gql-boards]}])

(def UserByToken
  [::re-graph/query
   {:instance-id :a
    :id :UserByToken
    :query "{UserByToken {... on User {userName accessLevel fullName id} ... on NotFoundAcceptableNull {message}}}"
    :callback [::events/user-info]}])

(defn PostsByBoard [x]
  [::re-graph/query
   {:instance-id :a
    :id :PostsByBoard
    :query "($board: Int) {PostsByBoard(board: $board) {title id comments {id} user {userName}}}"
    :variables {:board (js/Number x)}
    :callback [::events/get-gql-board]}])

(defn GetPost [x]
  [::re-graph/query
   {:instance-id :a
    :id :GetPost
    :query "($id: Int) {GetPost(id: $id) {title body id user {userName} comments {body id parent user {userName}}}}"
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

(defn MessageSub
  [x]
  [::re-graph/subscribe
   {:instance-id :b
    :id :MessageSub
    :query "($token: String){MessageSub(token: $token) {message from}}"
    :variables {:token x}
    :callback [::events/receive-message]}])

(defn SendMessage
  [[x y]]
  [::re-graph/mutate
   {:instance-id :a
    :id :SendMessage
    :query "($user: String $body: String) {SendMessage(user: $user message: $body)}"
    :variables {:user x :body y}
    :callback [::events/do-nothing]}])

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
  [[x y]]
  [::re-graph/mutate
   {:instance-id :a
    :query "($username: String $password: String) {CreateUser(username: $username password: $password)}"
    :variables {:username x :password y}
    :callback [::events/do-nothing]}])

(def TheGodlyPickle
  [::re-graph/subscribe
   {:instance-id :b
    :id :TheGodlyPickle
    :query "{TheGodlyPickle}"
    :callback [::events/do-nothing]}])

(defn CreateComment
  [x y z]
  [::re-graph/mutate
   {:instance-id :a
   ;; :id :CreateComment
    :query "($body: String $post: Int $parent: Int) {CreateComment(body: $body post: $post parent: $parent) {body id parent user {userName}}}"
    :variables {:body x :post y :parent z}
    :callback [::events/update-comment nil]}])

(defn DeleteComment
  [x]
  [::re-graph/mutate
   {:instance-id :a
   ;; :id :CreateComment
    :query "($id: Int) {DeleteComment(id: $id)}"
    :variables {:id x}
    :callback [::events/update-comment {:id (js/Number x)}]}])

(defn AddMembers
  [x y]
  [::re-graph/mutate
   {:instance-id :a
   ;; :id :CreateComment
    :query "($users: [Int] $board: Int) {AddMembers(users: $users board: $board) {... on User {id} ... on NotFoundAcceptableNull {message}}}"
    :variables {:users x :board y}
    :callback [::events/do-nothing]}])

(defn CreateBoard
  [x]
  [::re-graph/mutate
   {:instance-id :a
    :query "($title: String) {CreateBoard(title: $title)}"
    :variables {:title x}
    :callback [::events/do-nothing]}])
