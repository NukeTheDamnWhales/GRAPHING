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
                [:a {:href (routes/url-for first-url-part :id (:id x))} (str (:title x))]
                (when (= first-url-part :post)
                  [:p (str "| Author: " (:userName (first (:user x))) " | Comments: " (count (:comments x)) " |")])])
             elements)])



(defn home-panel []
  (let [login-or-logout (re-frame/subscribe [::subs/auth-sub])]
    [:div.home-panel
     [:header.home-header "PICKLES"]
     [:a {:href (routes/url-for :user)} "User"]
     [:a {:href (routes/url-for :boards)} "Boards"]
     [:a {:href (if @login-or-logout
                  (routes/url-for :logout :logout-action "logout")
                  (routes/url-for :auth))}
      (if @login-or-logout "Logout" "Login")]]))
;; about



(defn user-panel []
  (let [userbytoken (re-frame/subscribe [::subs/user-info])
        userinfo (:UserByToken @userbytoken)]
    [:div.user-panel
     [:table
      [:tr
       [:th "Username"]
       [:th "Full Name"]
       [:th "User Id"]
       [:th "User Access"]]
      [:tr
       [:td (:userName userinfo)]
       [:td (:fullName userinfo)]
       [:td (:id userinfo)]
       [:td (:accessLevel userinfo)]]]]))



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
    [:div.post-panel (make-link-list :post @board)
     [:a.create-user-link {:href (routes/url-for :create-post :id @board-id)} "create post"]
     ]))

(defn make-comment-tree
  ([comments]
   (make-comment-tree (filter (fn [x] (if (:parent x) nil x)) comments)
                      (filter (fn [x] (if (:parent x) x nil)) comments)))
  ([first-level comments]
   (map (fn [x] (let [children (filter (fn [y] (= (:id x) (:parent y))) comments)]
                 (assoc-in x [:children] (if (not-empty children)
                                           (make-comment-tree children comments)
                                           children))))
        first-level)))
;; (set! (-> (. js/document (getElementById "12")) .-style .-display) "none")

;; (defn child-comment [parent create-comment]
;;   [:form {:onSubmit (fn [e]
;;                       (let [body @create-comment]
;;                         (prn body)
;;                         (.preventDefault e)
;;                         (re-frame/dispatch (graphql/CreateComment body id parent))))}
;;    [:input {:type :text
;;             :value @create-comment
;;             :on-change (fn [x]
;;                          (re-frame/dispatch [::events/create-comment (-> x .-target .-value)]))}]
;;    [:input {:type :submit :value "submit"}]])


(defn make-comment-tree-hiccup
  [comments parent-id active-reply child-comment]
  (let [{:keys [body user id children]} comments
        child-list (conj (map (fn [x] (:id x)) children) id)]
    [:div {:key (str "close-" id)}
     [:input.collapse {:id "toggle" :type "checkbox":onChange (fn [e]
                                                       (into []
                                                             (map (fn [y]
                                                                    (let [element (. js/document (getElementById (str y)))]
                                                                      (if (= (-> element .-style .-display) "none")
                                                                        (set! (-> element .-style .-display) "block")
                                                                        (set! (-> element .-style .-display) "none")))))
                                                             child-list))}]
     [:ul.c-auth {:key id :class (str child-list) :id id} (:userName (first user))
      [:li.body body]
      [:a.reply {:onClick (fn [e]
                            (.preventDefault e)
                            (re-frame/dispatch [::events/clear-comment])
                            (re-frame/dispatch [::events/active-reply id]))} "reply"]
      (when (= id @active-reply)
        (child-comment id))
      (when (not-empty children)
        (into [:ul] (map (fn [x] (make-comment-tree-hiccup x parent-id active-reply child-comment))) children))]]))


;; Need to modify this to traverse comments and make a tree
;; Also why the heck is the Comments form a lazy seq ?? probably because it returns derefs
(defn post-panel []
  (let [post (re-frame/subscribe [::subs/current-post])
        {:keys [title body user comments id]} @post
        create-comment (re-frame/subscribe [::subs/create-comment])
        child-comment (fn [parent]
                        [:form {:onSubmit (fn [e]
                                            (let [body @create-comment]
                                              (prn body)
                                              (.preventDefault e)
                                              (re-frame/dispatch (graphql/CreateComment body id parent))
                                              (re-frame/dispatch (graphql/GetPost (:id id)))
                                              (re-frame/dispatch [::events/clear-comment])))}
                         [:input {:type :text
                                  :value @create-comment
                                  :on-change (fn [x]
                                               (re-frame/dispatch [::events/create-comment (-> x .-target .-value)]))}]
                         [:input {:type :submit :value "submit"}]])
        active-reply (re-frame/subscribe [::subs/active-reply])]
    [:div.single-post-panel
     [:header.top title
      [:p body]
      [:header (str "Author: " (:userName (first user)))]]
     [:br]
     (child-comment nil)
     [:br]
     [:header.comment "Comments: "
      (into [:div]
            (map (fn [x] (make-comment-tree-hiccup x id active-reply child-comment)))
            (make-comment-tree comments))
      [:br]
      ;; (doall (map (fn [x] (let [{:keys [body id user]} x]
      ;;                       [:header.c-auth {:key id} (str "Author: " (:userName (first user)))
      ;;                        [:p (str " " body)]
      ;;                        [:form {:onSubmit (fn [e]
      ;;                                            (.preventDefault e)
      ;;                                            (re-frame/dispatch [::events/active-reply id]))}
      ;;                         [:input.reply {:type :submit :value "Reply"}]]
      ;;                        (when (= id @active-reply)
      ;;                          (child-comment id))])) comments))
      ]]))



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
    (let [users (re-frame/subscribe [::subs/logged-in-users])
          user-list (:LoggedInUsers @users)]
      [:header.logged-in "currently online:" [:div (str user-list)]])))



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
    :user-panel [user-panel]
    [:div "You are horribly horribly lost 🥲"]))



(defn show-panel [panel-name]
  [panels panel-name])



(defn main-panel []
  ;; Testing zone
  ;; (re-frame/dispatch graphql/TheGodlyPickle)
  ;; -----------
  (js/setInterval
   (fn []
     (if (.getItem (.-localStorage js/window) "Authorization")
       (re-frame/dispatch graphql/RefreshToken)
       nil))
   (* 2 60 1000))
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
