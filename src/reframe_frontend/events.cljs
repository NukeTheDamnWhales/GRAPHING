(ns reframe-frontend.events
  (:require [re-frame.core :as re-frame]
            [re-graph.core :as re-graph]))

(re-frame/reg-event-db
 ::set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/reg-event-db
 ::get-gql-boards
 [re-frame/unwrap]
 (fn [db {:keys [response]}]
   (let [{:keys [data errors]} response]
     (assoc db :all-boards data))))

(re-frame/reg-event-db
 ::get-gql-board
 [re-frame/unwrap]
 (fn [db {:keys [response]}]
   (let [{:keys [data errors]} response]
     (prn data)
     (assoc db :current-board data))))

(re-frame/reg-event-db
 ::get-gql-post
 [re-frame/unwrap]
 (fn [db {:keys [response]}]
   (let [{:keys [data errors]} response]
     (assoc db :current-post data))))

(re-frame/reg-event-db
 ::login-panel
 (fn [db [_ x y]]
   (assoc-in db [:login-panel x] y)))

(re-frame/reg-event-db
 ::handle-auth
 [re-frame/unwrap]
 (fn [db {:keys [response]}]
   (let [{:keys [data errors]} response
         jwt (first (vals data))]
     (if (or errors (= (:RefreshToken data) "error"))
       ;; (do
       ;;   ;; (.clear (.-localStorage js/window))
       ;;   (dissoc db :authorization))
       db
       (do
         (.setItem (.-localStorage js/window) "Authorization" jwt)
         (assoc db :authorization jwt))))))

(re-frame/reg-event-db
 ::reload-auth
 (fn [db [_ x]]
   (let [authorization (.getItem (.-localStorage js/window) "Authorization")]
     (if (= authorization "error")
       ;; (.clear (.-localStorage js/window))
         db
       (assoc db :authorization authorization)))))

(re-frame/reg-event-db
 ::logged-in-users
 (fn [db [_ x]]
   (assoc db :logged-in-users (-> x :response :data))))

(re-frame/reg-event-db
 ::log-out
 (fn [db]
   (.clear (.-localStorage js/window))
   (dissoc db :authorization)))

(re-frame/reg-event-db
 ::create-post
 (fn [db [_ x y]]
   (assoc-in db [:create-post x] y)))

(re-frame/reg-event-db
 ::create-comment
 (fn [db [_ x]]
   (assoc db :create-comment x)))

(re-frame/reg-event-db
 ::set-board-id
 (fn [db [_ x]]
   (assoc db :current-board-id (js/Number x))))

(re-frame/reg-event-db
 ::do-nothing
 (fn [db]
   db))

(re-frame/reg-event-db
 ::create-user
 (fn [db [_ x y]]
   (assoc-in db [:create-user x] y)))

(re-frame/reg-event-db
 ::user-info
 [re-frame/unwrap]
 (fn [db {:keys [response]}]
   (let [{:keys [data errors]} response]
     (assoc db :user-info data))))

(re-frame/reg-event-db
 ::active-reply
 (fn [db [_ x]]
   (assoc db :active-reply x)))

(re-frame/reg-event-db
 ::clear-comment
 (fn [db]
   (dissoc db :create-comment)))


(re-frame/reg-event-db
 ::update-comment
 [re-frame/unwrap]
 (fn [db {:keys [response]}]
   (let [{:keys [data errors]} response
         comments (-> db :current-post :GetPost :comments)]
     (reduce-kv (fn [m k v]
                  (assoc-in m [:current-post :GetPost :comments] (if (= :CreateComment k)
                                                                   (conj comments (k data))
                                                                   (remove #(= v (:id %)) comments)))) db data))))

(re-frame/reg-event-db
 ::send-message
 (fn [db [_ x y]]
   (assoc-in db [:send-message x] y)))

(re-frame/reg-event-db
 ::receive-message
 (fn [db [_ x]]
   (let [mes (-> x :response :data :MessageSub)]
     (if mes
       (update-in db [:receive-message] conj mes)
       db))))
