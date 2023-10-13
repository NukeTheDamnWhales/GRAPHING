(ns reframe-frontend.views
  (:require
   [re-frame.core :as re-frame]
   [re-graph.core :as re-graph]
   [reframe-frontend.routes :as routes]
   [reframe-frontend.subs :as subs]
   [reframe-frontend.events :as events]
   [reframe-frontend.graphql :as graphql]))

(defn make-link-list [first-url-part elements]
  [:div (map (fn [x]
               [:div
                {:key (str (:id x))}
                [:a {:href (routes/url-for first-url-part :id (:id x))} (str (:title x))]])
             elements)])

(defn home-panel []
  (let [login-or-logout (re-frame/subscribe [::subs/auth-sub])]
    [:div.home-panel
     [:header.home-header "PICKLES"]
     [:a {:href (routes/url-for :about)} "About"]
     [:a {:href (routes/url-for :boards)} "Boards"]
     [:a {:href (if @login-or-logout
                  (routes/url-for :logout :logout-action "logout")
                  (routes/url-for :auth))}
      (if @login-or-logout "Logout" "Login")]]))
;; about


(defn about-panel []
  [:div "This is the About Page."
   [:div [:a {:href (routes/url-for :home)} "go to Home Page"]]
   [:div [:a {:href (routes/url-for :boards)} "go to Boards Page"]]])

(defn login-panel []
  (let [login (re-frame/subscribe [::subs/login-form])]
    [:div
     [:a {:href (routes/url-for :create-user)} "Create a new user"]
     [:form {:onSubmit (fn [e]
                         (let [{:keys [username password]} @login]
                           (.preventDefault e)
                           ;; (.replace (.-location js/window) "/auth")
                           (re-frame/dispatch (graphql/LogIn username password))))}
      [:input {:type :text
               :value (:username @login)
               :on-change (fn [x]
                            (re-frame/dispatch [::events/login-panel :username (-> x .-target .-value)]))}]
      [:input {:type :text
               :value (:password @login)
               :on-change (fn [x]
                            (re-frame/dispatch [::events/login-panel :password (-> x .-target .-value)]))}]
      [:input {:type :submit :value "submit"}]]]))

(defn create-user-panel []
  (let [create-user (re-frame/subscribe [::subs/create-user-form])]
    [:div
     [:form {:onSubmit (fn [e]
                         (let [{:keys [username password]} @create-user]
                           (.preventDefault e)
                           ;; (.replace (.-location js/window) "/auth")
                           (re-frame/dispatch (graphql/CreateUser username password))))}
      [:input {:type :text
               :value (:username @create-user)
               :on-change (fn [x]
                            (re-frame/dispatch [::events/create-user :username (-> x .-target .-value)]))}]
      [:input {:type :text
               :value (:password @create-user)
               :on-change (fn [x]
                            (re-frame/dispatch [::events/create-user :password (-> x .-target .-value)]))}]
      [:input {:type :submit :value "submit"}]]]))

(defn boards-panel []
  (let [board (re-frame/subscribe [::subs/all-boards])]
    [:div.link-list
     (make-link-list :single-board @board)]))

(defn single-board-panel []
  (let [board (re-frame/subscribe [::subs/current-board])
        board-id (re-frame/subscribe [::subs/current-board-id])]
    [:div (make-link-list :post @board)
     [:a {:href (routes/url-for :create-post :id @board-id)} "create post"]
     ]))

(defn post-panel []
  (let [post (re-frame/subscribe [::subs/current-post])]
    [:div (str @post)]))

(defn create-post-panel []
  (let [create-post (re-frame/subscribe [::subs/create-post])
        board (re-frame/subscribe [::subs/current-board-id])]
    [:div
     [:form {:onSubmit (fn [e]
                         (let [{:keys [title body]} @create-post]
                           (.preventDefault e)
                           ;; (.replace (.-location js/window) "/auth")
                           (re-frame/dispatch (graphql/CreatePost title body @board))))}
      [:input {:type :text
               :value (:title @create-post)
               :on-change (fn [x]
                            (re-frame/dispatch [::events/create-post :title (-> x .-target .-value)]))}]
      [:input {:type :text
               :value (:body @create-post)
               :on-change (fn [x]
                            (re-frame/dispatch [::events/create-post :body (-> x .-target .-value)]))}]
      [:input {:type :submit :value "submit"}]]]))

(defn users-online []
  (re-frame/dispatch graphql/OnlineUsers)
  (fn []
    (let [users (re-frame/subscribe [::subs/logged-in-users])]
      [:div (str @users)])))

;; main

(defn- panels [panel-name]
  (case panel-name
    :home-panel [home-panel]
    :about-panel [about-panel]
    :boards-panel [boards-panel]
    :single-board-panel [single-board-panel]
    :post-panel [post-panel]
    :auth-panel [login-panel]
    :logout-panel [about-panel]
    :create-post-panel [create-post-panel]
    :create-user-panel [create-user-panel]
    [:div "404 not found and what not :'-( single page routing is hard"]))

(defn show-panel [panel-name]
  [panels panel-name])

(defn main-panel []
  (js/setInterval
   (fn []
     (if (.getItem (.-localStorage js/window) "Authorization")
       (re-frame/dispatch graphql/RefreshToken)
       nil))
   (* 4 60 1000))
  (re-frame/dispatch [::events/reload-auth])
  (fn []
    (let [active-panel (re-frame/subscribe [::subs/active-panel])
          auth-reload (re-frame/subscribe [::subs/auth-sub])]
      ;; Reload auth key on all changes to auth db (stored in localstorage)
      (re-frame/dispatch [::re-graph/re-init {:instance-id :a
                                              :http {:impl (if @auth-reload
                                                             {:headers {"Authorization" @auth-reload}}
                                                             nil)} :ws nil}])
      [:div
       [home-panel]
       [show-panel @active-panel]
       [users-online]])))