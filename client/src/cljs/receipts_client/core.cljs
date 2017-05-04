;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.core
    (:require [reagent.core :as reagent]
              [re-frame.core :as re-frame]
              [re-frisk.core :refer [enable-re-frisk!]]
              [receipts-client.events]
              [receipts-client.subs]
              [receipts-client.routes :as routes]
              [receipts-client.views :as views]
              [receipts-client.config :as config]))


(defn dev-setup []
  (enable-console-print!)
  (when config/debug?
    (enable-re-frisk!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (dev-setup)
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (mount-root))
