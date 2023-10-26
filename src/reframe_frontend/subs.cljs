(ns reframe-frontend.subs
  (:require [re-frame.core :as re-frame]
            [re-graph.core :as re-graph]))

(re-frame/reg-sub
 ::name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 ::active-panel
 (fn [db _]
   (:active-panel db)))

(re-frame/reg-sub
 ::all-boards
 (fn [db _]
   (:GetAllBoards (:all-boards db))))

(re-frame/reg-sub
 ::current-board
 (fn [db _]
   (:PostsByBoard (:current-board db))))

(re-frame/reg-sub
 ::current-post
 (fn [db _]
   (:GetPost (:current-post db))))

(re-frame/reg-sub
 ::login-form
 (fn [db _]
   (:login-panel db)))

(re-frame/reg-sub
 ::logged-in-users
 (fn [db _]
   (:logged-in-users db)))

(re-frame/reg-sub
 ::auth-sub
 (fn [db _]
   (:authorization db)))

(re-frame/reg-sub
 ::create-post
 (fn [db _]
   (:create-post db)))

(re-frame/reg-sub
 ::current-board-id
 (fn [db _]
   (:current-board-id db)))

(re-frame/reg-sub
 ::create-user-form
 (fn [db _]
   (:create-user db)))

(re-frame/reg-sub
 ::user-info
 (fn [db _]
   (:user-info db)))

(re-frame/reg-sub
 ::create-comment
 (fn [db _]
   (:create-comment db)))

(re-frame/reg-sub
 ::active-reply
 (fn [db _]
   (:active-reply db)))

(re-frame/reg-sub
 ::send-message
 (fn [db _]
   (:send-message db)))

(re-frame/reg-sub
 ::receive-message
 (fn [db _]
   (:receive-message db)))

(re-frame/reg-sub
 ::create-board
 (fn [db _]
   (:create-board db)))

(re-frame/reg-sub
 ::toggle-messages
 (fn [db _]
   (:toggle-messages db)))

(re-frame/reg-sub
 ::secure-pickle-return
 (fn [db _]
   (:secure-pickle-return db)))

(re-frame/reg-sub
 ::secure-pickle
 (fn [db _]
   (:secure-pickle db)))
