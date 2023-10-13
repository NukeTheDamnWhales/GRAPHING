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
