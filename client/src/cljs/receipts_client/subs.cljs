;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

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
 :history
 (fn [db _]
   (:history db)))

(re-frame/reg-sub
 :schema
 (fn schema [db _]
   (get db :schema)))

(re-frame/reg-sub
 :categories
 (fn categories [db _]
   (get-in db [:schema :categories])))

(re-frame/reg-sub
 :vendors
 (fn vendors [db _]
   (get-in db [:schema :vendors])))

(re-frame/reg-sub
 :users
 (fn users [db _]
   (get-in db [:schema :users])))

(re-frame/reg-sub
 :payment-methods
 (fn payment-methods [db _]
   (get-in db [:schema :payment-methods])))

(re-frame/reg-sub
 :current-receipt
 (fn current-receipt [db _]
   (get db :current-receipt)))
