;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [receipts-client.utils :as utils]))

(re-frame/reg-sub
 :name
 (fn [db]
   (:name db)))

(re-frame/reg-sub
 :page
 (fn [db _]
   (:page db)))

(re-frame/reg-sub
 :server
 (fn [db _]
   (:server db)))

(re-frame/reg-sub
 :about-server
 (fn [db _]
   (get-in db [:about :server])))

(re-frame/reg-sub
 :history
 (fn [db _]
   (get-in db [:history :purchases])))

(re-frame/reg-sub
 :history-csv
 (fn [db _]
   (get-in db [:history :csv])))

(re-frame/reg-sub
 :schema
 (fn schema [db _]
   (get db :schema)))

(re-frame/reg-sub
 :credentials
 (fn credentials [db _]
   (-> db :credentials ((:server db)))))

(re-frame/reg-sub
 :user
 (fn user [db _]
   (let [credentials (-> db :credentials ((:server db)))
         users (-> db :schema :users) ]
     (utils/get-at users :user/email (:user/email credentials)))))

(re-frame/reg-sub
 :categories
 (fn categories [db _]
   (or (get-in db [:schema :categories]) [])))

(re-frame/reg-sub
 :vendors
 (fn vendors [db _]
   (or (get-in db [:schema :vendors]) [])))

(re-frame/reg-sub
 :users
 (fn users [db _]
   (or (get-in db [:schema :users]) [])))

(re-frame/reg-sub
 :payment-methods
 (fn payment-methods [db _]
   (or (get-in db [:schema :payment-methods]) [])))

(re-frame/reg-sub
 :current-receipt
 (fn current-receipt [db _]
   (get db :current-receipt)))
