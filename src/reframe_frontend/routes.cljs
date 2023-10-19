(ns reframe-frontend.routes
  (:require
   [pushy.core :as pushy]
   [bidi.bidi :as bidi]
   [bidi.verbose :refer [branch param leaf]]
   [re-frame.core :as re-frame]
   [reframe-frontend.events :as events]
   [reframe-frontend.graphql :as graphql]))

;; (def routes ["/" {""      :home
;;                   "about" :about
;;                   "boards" {"" :boards
;;                             ["/" :id] :single-board
;;                             ["/" :id :create] :create-post}
;;                   ["post/" :id] :post
;;                   "auth" {"" :auth
;;                           ["/" :logout-action] :logout}}])

(def routes (branch
              "/"
              (leaf "" :home)
              (leaf "about" :about)
              (leaf "user" :user)
              (branch "boards"
                      (leaf "" :boards)
                      (branch "/" (param :id)
                              (leaf "" :single-board)
                              (leaf "/create" :create-post)))
              (branch "post/" (param :id)
                      (leaf "" :post))
              (branch "auth"
                      (leaf "" :auth)
                      (leaf "/logout" :logout)
                      (leaf "/create" :create-user))))

(defn- parse-url [url]
  (bidi/match-route routes url))


(defn- dispatch-route [matched-route]
  (let [panel-name (keyword (str (name (:handler matched-route)) "-panel"))]
    ;; (when (not (= panel-name :logout-panel))
    ;;   (re-frame/dispatch [::events/set-active-panel panel-name]))
    (re-frame/dispatch [::events/set-active-panel panel-name])
    (case panel-name
      :boards-panel
      (re-frame/dispatch graphql/GetAllBoards)
      :single-board-panel
      (do (re-frame/dispatch (graphql/PostsByBoard (:id (:route-params matched-route))))
          (re-frame/dispatch [::events/set-board-id (:id (:route-params matched-route))]))
      :post-panel
      (re-frame/dispatch (graphql/GetPost (:id (:route-params matched-route))))
      :logout-panel
      (re-frame/dispatch graphql/LogOut)
      :user-panel
      (re-frame/dispatch graphql/UserByToken)
      nil)))

(defn app-routes []
  (pushy/start! (pushy/pushy dispatch-route parse-url)))

(def url-for (partial bidi/path-for routes))
