(ns receipts-client.routes
    (:require-macros [secretary.core :refer [defroute]])
    (:import goog.History)
    (:require [secretary.core :as secretary]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [re-frame.core :as re-frame]))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")

  (defroute "/home" []
    (re-frame/dispatch [:set-active-panel :home]))

  (defroute "/history" []
    (re-frame/dispatch [:set-active-panel :history]))

  (defroute "/setup" []
    (re-frame/dispatch [:set-active-panel :setup]))

  (defroute "/about" []
    (re-frame/dispatch [:set-active-panel :about]))

  (hook-browser-navigation!))

(defn goto-page [page]
  (re-frame/dispatch [:set-active-panel page])
  (aset js/window "location" (str "/#/" (name page))))
