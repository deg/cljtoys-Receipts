;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.events
  (:require
   [ajax.core :as ajax]
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [clojure.spec.alpha :as s]
   [com.degel.re-frame.storage]
   [day8.re-frame.http-fx]
   [re-frame.core :as re-frame]
   [receipts-client.api-client :as api]
   [receipts-client.db :as db]
   [receipts-client.routes :as routes]
   [receipts-client.subs :as subs]
   [receipts-client.utils :as utils]
   [receipts.specs :as specs]))

(s/check-asserts true)

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
   {:pre [(specs/validate ::specs/db db)
          (specs/validate keyword? page)
          (specs/validate (s/nilable keyword?) server)]}
   (into {:db (assoc db
                     :page page
                     :server (or server (:server db)))
          :goto-page [page server]}
         (case page
           :history {:dispatch [:get-history]}
           :home {:dispatch [:next-receipt]}
           :about {:dispatch [:get-about-server]}
           {}))))

(re-frame/reg-event-fx
 :set-server
 (fn set-server [{db :db} [_ server]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate keyword? server)]}
   {:db (dissoc db :schema)
    :dispatch [:set-page (:page db) server]
    :dispatch-later [{:ms 500 :dispatch [:get-schema :all]}]}))

(re-frame/reg-event-fx
 :login
 (fn login [{db :db} [_ email password]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate string? email)
          (specs/validate string? password)]}
   {:http-xhrio (api/get-request {:server (:server db)
                                  :api "login"
                                  :params {:user/email email
                                           :user/password password}
                                  :on-success [:got-login]})}))

(re-frame/reg-event-fx
 :got-login
 (fn got-login [{db :db} [_ {credentials :user/credentials}]]
    {:pre [(specs/validate ::specs/db db)
          (specs/validate :user/credentials credentials)]}
   (let [server (:server db)
         email (:user/email credentials)
         token (:user/token credentials)]
     {:storage/set {:pairs [{:name :email :value email}
                            {:name :token :value token}]}
      :dispatch-later [{:ms 100 :dispatch [:update-credentials]}]})))


(re-frame/reg-event-fx
 :logout
 (fn logout [{db :db} _]
   {:pre [(specs/validate ::specs/db db)]}
   {:db (dissoc db :credentials :schema)
    :storage/remove {:names [:email :token]}}))


(re-frame/reg-event-fx
 :update-credentials
 [(re-frame/inject-cofx :storage/get {:names [:email :token]})]
 (fn update-credentials [{db :db {:keys [email token]} :storage/get}]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate (s/nilable string?) email)
          (specs/validate (s/nilable string?) token)]}
   (when token
     {:db (-> db
              (assoc-in [:credentials (:server db) :user/email] email)
              (assoc-in [:credentials (:server db) :user/token] token))
      :dispatch-later [{:ms 500 :dispatch [:get-schema :all]}]})))

(defn entity-type
  "This is a bit hackish. It depends on each key in an entity map being in a
   namespace named the same as the api call"
  [entity]
  {:pre [(specs/validate map? entity)]
   :post [(specs/validate ::specs/entity-name %)]}
  (-> entity ffirst namespace))

(defn multi-post-request [db api requests]
  {:pre [(specs/validate ::specs/db db)
         (specs/validate ::specs/api api)
         (specs/validate (s/coll-of ::specs/entity) requests)]}
  (let [server (:server db)
        credentials (get-in db [:credentials server])]
    (api/post-request {:server server
                       :api api
                       :params {:credentials credentials
                                :payload (mapv api/string-keyed requests)}
                       :on-success [:get-schema api]})))

(re-frame/reg-event-fx
 :load-entities
 (fn load-entities [{db :db} [_ entities]]
   {:pre [(specs/validate (s/coll-of ::specs/entity) entities)]}
   (let [api-groups (group-by entity-type entities)]
     {:http-xhrio (mapv (fn [[api api-entities]]
                          (multi-post-request db api api-entities))
                        api-groups)})))

(re-frame/reg-event-fx
 :get-schema
 (fn get-schema [{db :db} [_ api]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate (s/or :all #{:all} :schema ::specs/api) api)]}
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

                             (when (#{:all "source"} api)
                               (api/get-request {:server server
                                                 :api "sources"
                                                 :params credentials
                                                 :on-success [:got-schema :sources]}))
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
   {:pre [(specs/validate ::specs/db db)
          (specs/validate ::specs/section section)
          (specs/validate (s/coll-of ::specs/entity) pull-response)]}
   (assoc-in db [:schema section] pull-response)))



(re-frame/reg-event-fx
 :get-history
 (fn get-history [{db :db} _]
   {:pre [(specs/validate ::specs/db db)]}
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
       {:dispatch [:get-schema :all]
        :http-xhrio (if admin? [formatted raw] [formatted])}))))

(re-frame/reg-event-db
 :got-history
 (fn got-history [db [_ pull-response]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate (s/coll-of ::specs/purchase) pull-response)]}
   (assoc-in db [:history :purchases] (sort-by :purchase/date pull-response))))

(re-frame/reg-event-db
 :got-csv-history
 (fn got-cvs-history [db [_ csv]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate (s/keys :req-un [::csv]) csv)]}
   (assoc-in db [:history :csv] (:csv csv))))

(re-frame/reg-event-fx
 :get-about-server
 (fn get-about-server [{db :db} _]
   {:pre [(specs/validate ::specs/db db)]}
   {:http-xhrio [(api/get-request {:server (:server db)
                                   :api "about"
                                   :params {}
                                   :on-success [:got-about-server]})]}))

(re-frame/reg-event-db
 :got-about-server
 (fn got-about-server [db [_ about-data]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate (s/keys :req-un [::version ::dependencies]) about-data)]}
   (assoc-in db [:about :server] about-data)))


(re-frame/reg-event-db
 :edit-current-receipt
 (fn edit-current-receipt [db [_ field value]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate ::specs/purchase-keys field)
          (specs/validate (s/or :date inst? :multi set? :other string?) value)]}
   (assoc-in db [:current-receipt field] value)))

(defn post-params [db api on-success params]
  {:pre [(specs/validate ::specs/db db)
         (specs/validate ::specs/api api)
         (specs/validate ::specs/event-vector on-success)]}
  (api/post-request
   {:server (:server db)
    :api api
    :params {:credentials (-> db :credentials ((:server db)))
             :payload [(api/string-keyed params)]}
    :on-success on-success}))

(re-frame/reg-event-fx
 :submit-receipt
 (fn submit-receipt [{db :db} [_ receipt]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate ::specs/purchase receipt)]}
   {:http-xhrio (post-params
                 db "purchase" [:submitted-receipt]
                 (assoc receipt
                        ;; [TODO]  Fixup use of Transit for Post (code location #4 for this issue)
                        :purchase/date (.toJSON (:purchase/date receipt))
                        ;; [TODO]  Need better UID
                        :purchase/uid (str "UID-" (.getTime (js/Date.)) "-" (rand-int 1000))
                        :purchase/price (:purchase/price receipt)
                        ;; [TODO] temp
                        :purchase/currency "NIS"))
    :db ;; [TODO] :previous-receipt not yet used. Remove this line if we don't do smart history or defaults
        (assoc db :previous-receipt receipt)}))

(defn reset-receipt [receipt]
  {:pre [(specs/validate (s/nilable ::specs/purchase) receipt)]
   :post [(specs/validate ::specs/purchase %)]}
  (assoc
   (dissoc receipt :purchase/price :purchase/category :purchase/vendor :purchase/consumer :purchase/comment)
   :purchase/date (time-coerce/to-date (time/now))))

(re-frame/reg-event-db
 :submitted-receipt
 (fn submitted-receipt [db _]
   {:pre [(specs/validate ::specs/db db)]}
   (assoc db :receipt-stored? true)))

(re-frame/reg-event-fx
 :next-receipt
 (fn next-receipt [{db :db}]
   {:pre [(specs/validate ::specs/db db)]}
   {:db (-> db
            (dissoc :receipt-stored?)
            (update :current-receipt reset-receipt))
    :dispatch [:get-schema :all]}))

(re-frame/reg-event-fx
 :add-user
 (fn add-user [{db :db} [_ {:keys [name password abbrev email permissions]}]]
   {:pre [(specs/validate ::specs/db db)
          (specs/validate (s/nilable string?) name)
          (specs/validate (s/nilable string?) password)
          (specs/validate (s/nilable string?) abbrev)
          (specs/validate (s/nilable string?) email)
          (specs/validate set? permissions)]}
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
   {:pre [(specs/validate ::specs/db db)
          (specs/validate :db/id category-id)
          (specs/validate string? vendor)]}
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
   (if (= status 0)
     (js/alert "Host not responding")
     (js/alert (goog.string/format "Error %d - %s\nIn %s %s\n\n%s" status status-text last-method uri details)))
   db))
