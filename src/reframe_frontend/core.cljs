(ns reframe-frontend.core
  (:require
   [re-graph.core :as re-graph]
   [re-frame.core :as re-frame]
   [reagent.dom :as rdom]
   [clojure.edn :as edn]
   [reframe-frontend.routes :as routes]
   [reframe-frontend.views :as views]
   [reframe-frontend.flagencoding :as flag]
   ))

;; replacing host dynamically, need config option

(def host {:http "http://127.0.0.1/api"
           :ws "ws://127.0.0.1/ws"})


(re-frame/reg-sub
 :active-panel
 (fn [db _]
   (:active-panel db)))

;; (defn panel1
;;  []
;;  [:div  {:on-click #(re-frame/dispatch [:set-active-panel :panel2])}
;;         "Here" ])

;; (defn panel2
;;  []
;;  [:div "There"])

;; (defn high-level-view
;;   []
;;   (let [active  (re-frame/subscribe [:active-panel])]
;;     (fn []
;;       [:div
;;        [:div.title   "Heading"]
;;        (prn @active)
;;        (condp = @active ;; or you could look up in a map
;;          :panel1   [panel1]
;;          :panel2   [panel2]
;;          [panel1])])))


;; ;; Initialize Graphql
(re-frame/dispatch [::re-graph/init {:instance-id :a
                                     :http {:url (:http host)}
                                     :ws nil
                                     }])

(re-frame/dispatch [::re-graph/init {:instance-id :b
                                     :ws {:url (:ws host)}
                                     }])

;; (re-frame/reg-event-db
;;  :on-thing
;;  [re-frame/unwrap]
;;  (fn [db _] (prn db)))


;; (re-frame/dispatch [::re-graph/mutate
;;                     {:id :login
;;                      :query "($username: String, $password: String) {LogIn(username: $username, password: $password)}"
;;                      :variables {:username "orgenborgen"
;;                                  :password "abc"}
;;                      :callback [:on-thing]}])


;; (defn app []
;;   [:div
;;    [:p "hi"]
;;    [high-level-view]])


(defn ^:export run []
  (rdom/render [views/main-panel] (js/document.getElementById "app")))


(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (routes/app-routes)
  (run))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (js/console.log "stop"))


;; PROPER WAY TO DISPATCH REFRAME

;; (defn mount-ui [] (rdom/render [ui] ;; mount the application's ui (js/document.getElementById
;; "dominoes-live-app"))) (defn run [] (rf/dispatch-sync [:initialize]) ;; puts a value into application state
;; (mount-ui))

;; When it comes to establishing initial application state, you'll notice the use of dispatch-sync, rather
;; than dispatch. This is a simplifying cheat which ensures that a correct structure exists in app-db
;; before any subscriptions or event handlers run. /
