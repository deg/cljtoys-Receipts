;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [cljs-time.core :as time]
             [cljs-time.coerce :as time-coerce]
             [com.degel.re-frame.storage]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]
             [receipts-client.preload :as preload]
             [receipts-client.routes :as routes]
             [receipts-client.subs :as subs]
             [receipts-client.utils :as utils]))


;;; [TODO] See comment at routes/goto-page
;;;        (re https://github.com/SMX-LTD/re-frame-document-fx)
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
   {:db (dissoc db :schema)
    :dispatch [:set-page (:page db) server]
    :dispatch-later [{:ms 500 :dispatch [:get-schema :all]}]}))

(re-frame/reg-event-fx
 :login
 (fn login [{db :db} [_ email password]]
   {:http-xhrio (api/get-request {:server (:server db)
                                  :api "login"
                                  :params {:user/email email
                                           :user/password password}
                                  :on-success [:got-login]})}))

(re-frame/reg-event-fx
 :got-login
 (fn got-login [{db :db} [_ {credentials :user/credentials}]]
   (let [server (:server db)
         email (:user/email credentials)
         token (:user/token credentials)]
     {:storage/set {:pairs [{:name :email :value email}
                            {:name :token :value token}]}
      :dispatch-later [{:ms 100 :dispatch [:update-credentials]}]})))


(re-frame/reg-event-fx
 :logout
 (fn logout [{db :db} _]
   {:db (dissoc db :credentials :schema)
    :storage/remove {:names [:email :token]}}))


(re-frame/reg-event-fx
 :update-credentials
 [(re-frame/inject-cofx :storage/get {:names [:email :token]})]
 (fn update-credentials [{db :db {:keys [email token]} :storage/get}]
   (when token
     {:db (-> db
              (assoc-in [:credentials (:server db) :user/email] email)
              (assoc-in [:credentials (:server db) :user/token] token))
      :dispatch-later [{:ms 500 :dispatch [:get-schema :all]}]})))

(re-frame/reg-event-fx
 :preload-base
  (fn preload-base [{db :db} _]
    {:http-xhrio (preload/initial-data
                  (:server db) (-> db :credentials ((:server db))))}))

(defn entity-type [entity]
  (-> entity ffirst namespace))

(re-frame/reg-event-fx
 :load-entities
 (fn load-entities [{db :db} [_ entities]]
   (let [api-groups (group-by entity-type entities)]
     {:http-xhrio (mapv (fn [[api api-entities]]
                          ;; [TODO] post-multi-request can be replaced by simpler code. Do after killing preload stuff
                          (let [handler (preload/post-multi-request api identity)]
                            (first (handler (:server db) (-> db :credentials ((:server db)))
                                            api-entities))))
                        api-groups)})))

(re-frame/reg-event-fx
 :get-schema
 (fn get-schema [{db :db} [_ api]]
   (let [server (:server db)
         credentials (when server (-> db :credentials server))]
     (when credentials
       {:http-xhrio (remove nil?
                            [(when (#{:all "category"} api)
                               (api/get-request {:server server
                                                 :api "categories"
                                                 :params credentials
                                                 :on-success [:got-schema :categories]}))
                             (when (#{:all "currency"} api)
                               (api/get-request {:server server
                                                 :api "currencies"
                                                 :params credentials
                                                 :on-success [:got-schema :currencies]}))

                             (when (#{:all "paymentMethod"} api)
                               (api/get-request {:server server
                                                 :api "paymentMethods"
                                                 :params credentials
                                                 :on-success [:got-schema :payment-methods]}))
                             (when (#{:all "user"} api)
                               (api/get-request {:server server
                                                 :api "users"
                                                 :params credentials
                                                 :on-success [:got-schema :users]}))
                             (when (#{:all "vendor"} api)
                               (api/get-request {:server server
                                                 :api "vendors"
                                                 :params credentials
                                                 :on-success [:got-schema :vendors]}))])}))))

(re-frame/reg-event-db
 :got-schema
 (fn got-schema [db [_ section pull-response]]
   (assoc-in db [:schema section] pull-response)))



(re-frame/reg-event-fx
 :get-history
 (fn get-history [{db :db} _]
   (let [server (:server db)
         credentials (-> db :credentials server)
         admin? (:user/isAdmin (subs/current-user db))
         formatted (api/get-request {:server server
                                     :api "purchases"
                                     :params credentials
                                     :on-success [:got-history]})
         raw (api/get-request {:server server
                               :api "csv-history"
                               :params credentials
                               :on-success [:got-csv-history]})]
     (when credentials
       {:http-xhrio (if admin? [formatted raw] [formatted])}))))

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

(defn post-params [db api on-success params]
  (api/post-request
   {:server (:server db)
    :api api
    :params {:credentials (-> db :credentials ((:server db)))
             :payload [(api/string-keyed params)]}
    :on-success on-success}))

(re-frame/reg-event-fx
 :submit-receipt
 (fn submit-receipt [{db :db} [_ receipt]]
   {:http-xhrio (post-params
                 db "purchase" [:submitted-receipt]
                 (assoc receipt
                        ;; [TODO]  Fixup use of Transit for Post (code location #4 for this issue)
                        :purchase/date (.toJSON (time-coerce/to-date (:purchase/date receipt)))
                        ;; [TODO]  Need better UID
                        :purchase/uid (str "UID-" (.getTime (js/Date.)) "-" (rand-int 1000))
                        :purchase/price (js/parseFloat (:purchase/price receipt))
                        ;; [TODO] temp
                        :purchase/currency "NIS"))
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
 :add-user
 (fn add-user [{db :db} [_ {:keys [name password abbrev email permissions] :as user}]]
   {:http-xhrio (let [admin? (contains? permissions :user/isAdmin)
                      editor? (contains? permissions :user/isEditor)
                      consumer? (contains? permissions :user/isConsumer)]
                  (post-params
                   db "user" [:get-schema "user"]
                   (utils/assoc-if {}
                     {"receipts/dynamic?" true
                      "user/name" name
                      "user/password" (when (or admin? editor?) password)
                      "user/abbrev" (when consumer? abbrev)
                      "user/email" (when (or admin? editor?) email)
                      "user/isAdmin" admin?
                      "user/isEditor" editor?
                      "user/isConsumer" consumer?})))}))

(re-frame/reg-event-fx
 :add-vendor
 (fn add-vendor [{db :db} [_ category-id vendor]]
   (let [categories (get-in db [:schema :categories])
         category (utils/get-at categories :db/id category-id :category/name)]
     {:http-xhrio (post-params
                   db "vendor" [:get-schema "vendor"]
                   {"receipts/dynamic?" true
                    "vendor/name" vendor
                    "vendor/category" category})})))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & [[_ {:keys [uri last-method status status-text] :as details}]]]
   (prn "Network connection failure: " details)
   (js/alert (goog.string/format "Error %d - %s\nIn %s %s\n\n%s" status status-text last-method uri details))
   db))
