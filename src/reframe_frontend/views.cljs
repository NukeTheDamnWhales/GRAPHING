(ns reframe-frontend.views
  (:require
   [clojure.string :as string]
   [re-frame.core :as re-frame]
   [re-graph.core :as re-graph]
   [reframe-frontend.routes :as routes]
   [reframe-frontend.subs :as subs]
   [reframe-frontend.events :as events]
   [reframe-frontend.graphql :as graphql]
   [cljs.reader :as reader]
   [clojure.walk :as walk]))



(defn make-link-list [first-url-part elements]
  [:div (map (fn [x]
               [:div
                {:key (str (:id x))}
                [:a {:href (routes/url-for first-url-part :id (:id x))} (str (:title x))]
                (cond
                  (= first-url-part :post) [:p (str "| Author: " (:userName (first (:user x))) " | Comments: " (-> x :comments count) " |")]
                  (= first-url-part :single-board)
                  [:p (str "| Creator: " (-> x :owner :userName) " | Members: "
                           (if (empty? (:members x))
                             "public"
                             (:members x)))])])
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

(defn make-input-form
  [subs dispatch keyvec]
  (let [[first-sub & rest-sub] subs
        [submit-event updatef & submit-side-effects] dispatch
        input-eventfun (fn [x y & r]
                         (fn [s]
                           (re-frame/dispatch (if (nil? y)
                                                [x (-> s .-target .-value)]
                                                [x y (-> s .-target .-value) (when r r)]))))
        sub1 (re-frame/subscribe [first-sub])
        submit-eventf (fn [x]
                        (fn [e]
                          (prn submit-side-effects)
                          (.preventDefault e)
                          (re-frame/dispatch (submit-event x))
                          (when (some? submit-side-effects)
                            (re-frame/dispatch (first submit-side-effects)))))]
    (fn []
      [:div
       (conj
        (into [:form
               {:onSubmit (submit-eventf (if (= 1 (count keyvec))
                                           @sub1
                                           (mapv (fn [x] (x @sub1)) keyvec)))}]
              (mapv (fn [field]
                      [:input {:type :text
                               :value (if (= 1 (count keyvec))
                                        @sub1
                                        (field @sub1))
                               :on-change (if (= 1 (count keyvec))
                                            (input-eventfun updatef nil)
                                            (input-eventfun updatef field))}])
                    keyvec))
        [:input {:type :submit :value "submit"}])])))

(defn boards-panel []
  (let [board (re-frame/subscribe [::subs/all-boards])]
    ;; (re-frame/dispatch (graphql/AddMembers [10 11 13] 0))
    [:div.link-list
     [:header "Create a new Board"
      [(make-input-form [::subs/create-board]
                        [graphql/CreateBoard ::events/create-board graphql/GetAllBoards]
                        [:title])]]
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
                 (assoc-in x [:children]
                           ;;changed not-empty to seq
                           (if (seq? children)
                             (make-comment-tree children comments)
                             children))))
        first-level)))




(defn make-comment-tree-hiccup
  [comments parent-id active-reply child-comment logged-in-user]
  (let [{:keys [body user id children]} comments]
    [:div {:key (str "close-" id)}
     [:input.collapse {:id "toggle" :type "checkbox" :onChange (fn [e]
                                                                 (let [element (. js/document (getElementById (str id)))]
                                                                   (if (= (-> element .-style .-display) "none")
                                                                     (set! (-> element .-style .-display) "block")
                                                                     (set! (-> element .-style .-display) "none"))))}]
     [:ul.c-auth {:key id :id id} (:userName (first user))
      [:li.body body]
      (if (= id @active-reply)
        [:div (child-comment id)
         [:a.cancel {:onClick (fn [e]
                               (.preventDefault e)
                               (re-frame/dispatch [::events/clear-comment])
                               (re-frame/dispatch [::events/active-reply nil]))} "cancel"]]
        [:a.reply {:onClick (fn [e]
                              (.preventDefault e)
                              (re-frame/dispatch [::events/clear-comment])
                              (re-frame/dispatch [::events/active-reply id]))} " reply "])
      (when (and logged-in-user (= (:userName (first user))
                                   (:user logged-in-user)))
        [:a.delete {:onClick (fn [e]
                               (re-frame/dispatch (graphql/DeleteComment id))
                               ;; (re-frame/dispatch (graphql/GetPost (:id id)))
                               )} " delete "])
      (when (not-empty children)
        (into [:ul] (map (fn [x] (make-comment-tree-hiccup x parent-id active-reply child-comment logged-in-user))) children))]]))



;; Need to modify this to traverse comments and make a tree
;; Also why the heck is the Comments form a lazy seq ?? probably because it returns derefs
(defn post-panel []
  (let [post (re-frame/subscribe [::subs/current-post])
        logged-in-token (re-frame/subscribe [::subs/auth-sub])
        logged-in-user (try (into {}
                                  (mapv (fn [x] (-> x js/atob (string/replace #":" "") (reader/read-string) walk/keywordize-keys))
                                        (pop (string/split @logged-in-token "."))))
                            (catch js/Error e
                              nil))
        ;; (map (fn [x] (prn x)(js/atob x)) (string/split @logged-in-token "."))
        {:keys [title body user comments id]} @post
        create-comment (re-frame/subscribe [::subs/create-comment])
        child-comment (fn [parent]
                        [:form {:onSubmit (fn [e]
                                            (let [body @create-comment]
                                              (.preventDefault e)
                                              (re-frame/dispatch (graphql/CreateComment body id parent))
                                              (re-frame/dispatch [::events/clear-comment])
                                              (re-frame/dispatch [::events/active-reply nil])))}
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
            (map (fn [x] (make-comment-tree-hiccup x id active-reply child-comment logged-in-user)))
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


(defn make-datalist-form
  [subs dispatch keyvec]
  (let [[first-sub data-sub response-sub & rest-sub] subs
        [submit-event updatef & submit-side-effects] dispatch
        input-eventfun (fn [x y & r]
                         (fn [s]
                           (re-frame/dispatch [x y (-> s .-target .-value) (when r r)])))
        sub1 (re-frame/subscribe [first-sub])
        datasub (re-frame/subscribe [data-sub])
        ressub (re-frame/subscribe [response-sub])
        submit-eventf (fn [x]
                        (fn [e]
                          (.preventDefault e)
                          (when (some? submit-side-effects)
                            (map identity submit-side-effects))
                          (re-frame/dispatch (submit-event x))))
        [firstfield secondfield] keyvec]
    (fn []
      [:div
       (conj (into [:select {:id "suggestions" :value (firstfield @sub1)
                             :on-change (input-eventfun updatef firstfield)}]
                   (mapv (fn [field]
                           [:option (get (string/split (str field) ":") 1)])
                         (-> @datasub :LoggedInUsers))))
       ;; [:input {:autoComplete "on" :list "suggestions" :value (firstfield @sub1)
       ;;          :on-change (input-eventfun updatef firstfield)}]
       [:form
        {:onSubmit (submit-eventf (mapv (fn [x] (x @sub1)) keyvec))}
        [:input {:type :text
                 :value (secondfield @sub1)
                 :on-change (input-eventfun updatef secondfield)}]
        [:input {:type :submit :value "send"}]]
       (when-let [messages @ressub]
         (into [:header] (map (fn [x] (prn x)
                                [:a (str x)])) messages))])))



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
        ;; (nil? @online)
        ;; (re-frame/dispatch [::re-graph/unsubscribe {:id :logged-in-users}])

        ;; (re-frame/dispatch graphql/OnlineUsers)
        ;; (re-frame/dispatch [::re-graph/unsubscribe {:id :logged-in-users}])
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
    :create-user-panel [(make-input-form [::subs/create-user-form]
                         [graphql/CreateUser ::events/create-user]
                         [:user :password])]
    :user-panel [user-panel]
    [:div "You are horribly horribly lost 🥲"]))


(defn show-panel [panel-name]
  [panels panel-name])



(defn main-panel []
  ;; Testing zone
  ;; -----------
  (js/setInterval
   (fn []
     (if (.getItem (.-localStorage js/window) "Authorization")
       (re-frame/dispatch graphql/RefreshToken)
       nil))
   (* 2 60 1000))

  (fn []
    (let [active-panel (re-frame/subscribe [::subs/active-panel])
          auth-reload (re-frame/subscribe [::subs/auth-sub])]
      ;; Reload auth key on all changes to auth db (stored in localstorage)
      (re-frame/dispatch [::events/reload-auth])
      (re-frame/dispatch (graphql/SecurePickleChannel "hi"))
      (re-frame/dispatch [::re-graph/re-init {:instance-id :a
                                              :http {:impl (if @auth-reload
                                                             {:headers {"Authorization" @auth-reload}}
                                                             nil)} :ws nil}])
      (if (and (nil? @auth-reload) (not= "null" @auth-reload))
        ;; (re-frame/dispatch [::events/clear-messages])
        (re-frame/dispatch [::re-graph/unsubscribe {:id :MessageSub}])
        (re-frame/dispatch (graphql/MessageSub (str @auth-reload))))
  ;; (re-frame/dispatch (graphql/SendMessage "orgenborgen" "hi"))
      [:div
       [home-panel]
       [show-panel @active-panel]
       [users-online]
       ;; [messages]
       [(make-datalist-form [::subs/send-message ::subs/logged-in-users ::subs/receive-message]
                            [graphql/SendMessage ::events/send-message]
                            [:user :body])]])))
