;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [cljs-time.core :as time]
             [cljs-time.coerce :as time-coerce]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]
             [receipts-client.preload :as preload]
             [receipts-client.routes :as routes]
             [receipts-client.utils :as utils]))

(re-frame/reg-fx
 :goto-page
 (fn goto-page [[page server]]
   (routes/goto-page page server)))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-fx
 :set-page
 (fn [{db :db} [_ page server]]
   (into {:db (assoc db
                     :page page
                     :server (or server (:server db)))
          :goto-page [page server]}
         (case page
           :history {:dispatch [:get-history]}
           :home {:dispatch [:submitted-receipt]}
           :about {:dispatch [:get-about-server]}
           {}))))

(re-frame/reg-event-fx
 :set-server
 (fn set-server [{db :db} [_ server]]
   {:dispatch [:set-page (:page db) server]
    :dispatch-later [{:ms 500 :dispatch [:get-schema :all]}]}))


(re-frame/reg-event-fx
 :preload-base
  (fn preload-base [{db :db} _]
    {:http-xhrio (preload/initial-data (:server db))
     :db  (assoc db :loading? true)}))

(re-frame/reg-event-fx
 :get-schema
  (fn get-schema [{db :db} [_ api]]
    (let [server (:server db)]
      {:http-xhrio (remove nil?
                           [(when (#{:all "category"} api)
                              (api/get-request {:server server
                                                :api "categories"
                                                :params {}
                                                :on-success [:got-schema :categories]}))
                            (when (#{:all "currency"} api)
                              (api/get-request {:server server
                                                :api "currencies"
                                                :params {}
                                                :on-success [:got-schema :currencies]}))

                            (when (#{:all "paymentMethod"} api)
                              (api/get-request {:server server
                                                :api "paymentMethods"
                                                :params {}
                                                :on-success [:got-schema :payment-methods]}))
                            (when (#{:all "user"} api)
                              (api/get-request {:server server
                                                :api "users"
                                                :params {}
                                                :on-success [:got-schema :users]}))
                            (when (#{:all "vendor"} api)
                              (api/get-request {:server server
                                                :api "vendors"
                                                :params {}
                                                :on-success [:got-schema :vendors]}))])
       :db  (assoc db :loading? true)})))

(re-frame/reg-event-db
 :got-schema
 (fn got-schema [db [_ section pull-response]]
   (assoc-in db [:schema section] pull-response)))



(re-frame/reg-event-fx
 :get-history
 (fn get-history [{db :db} _]
   {:http-xhrio [(api/get-request {:server (:server db)
                                   :api "purchases"
                                   :params {}
                                   :on-success [:got-history]})
                 (api/get-request {:server (:server db)
                                   :api "csv-history"
                                   :params {}
                                   :on-success [:got-csv-history]})]}))

(re-frame/reg-event-db
 :got-history
 (fn got-history [db [_ pull-response]]
   (assoc-in db [:history :purchases] (sort-by :purchase/date pull-response))))

(re-frame/reg-event-db
 :got-csv-history
 (fn got-cvs-history [db [_ csv]]
   (assoc-in db [:history :csv] (:csv csv))))

(re-frame/reg-event-fx
 :get-about-server
 (fn get-about-server [{db :db} _]
   {:http-xhrio [(api/get-request {:server (:server db)
                                   :api "about"
                                   :params {}
                                   :on-success [:got-about-server]})]}))

(re-frame/reg-event-db
 :got-about-server
 (fn got-about-server [db [_ about-data]]
   (assoc-in db [:about :server] about-data)))


(re-frame/reg-event-db
 :edit-current-receipt
 (fn edit-current-receipt [db [_ field value]]
   (assoc-in db [:current-receipt field] value)))

(re-frame/reg-event-fx
 :submit-receipt
 (fn submit-receipt [{db :db} [_ receipt]]
   {:http-xhrio (api/post-request {:server (:server db)
                                   :api "purchase"
                                   :params (assoc receipt
                                                  ;; [TODO]  Fixup use of Transit for Post (code location #4 for this issue)
                                                  :purchase/date (.toJSON (time-coerce/to-date (:purchase/date receipt)))
                                                  ;; [TODO]  Need better UID
                                                  :purchase/uid (str "UID-" (.getTime (js/Date.)) "-" (rand-int 1000))
                                                  :purchase/price (js/parseFloat (:purchase/price receipt))
                                                  ;; [TODO] temp
                                                  :purchase/currency "NIS")
                                   :on-success [:submitted-receipt]}
                 ;; [TODO] Need to handle server error, and restore previous receipt
                 ;; [TODO] Need to go to page that prevents new data entry until previous processed
                 )
    :db (assoc db
               :previous-receipt receipt)}))

(defn reset-receipt [receipt]
  (assoc
   (dissoc receipt :purchase/price :purchase/category :purchase/vendor :purchase/forWhom :purchase/comment)
   :purchase/date (time/now)))

(re-frame/reg-event-db
 :submitted-receipt
 (fn submitted-receipt [db [_ response]]
   (update db :current-receipt reset-receipt)))

(re-frame/reg-event-fx
 :add-vendor
 (fn add-vendor [{db :db} [_ category-id vendor]]
   (let [categories (get-in db [:schema :categories])
         category (utils/get-at categories :db/id category-id :category/name)]
     {:http-xhrio (api/post-request {:server (:server db)
                                     :api "vendor"
                                     :params {"receipts/dynamic?" true
                                              "vendor/name" vendor
                                              "vendor/category" category}
                                     :on-success [:get-schema "vendor"]})})))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & [[_ {:keys [uri last-method status status-text] :as details}]]]
   (prn "Network connection failure: " details)
   (js/alert (goog.string/format "Error %d - %s\nIn %s %s\n\n%s" status status-text last-method uri details))
   db))
