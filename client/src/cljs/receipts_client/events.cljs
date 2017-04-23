(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [cljs-time.core :as time]
             [cljs-time.coerce :as time-coerce]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]
             [receipts-client.preload :as preload]
             [receipts-client.routes :as routes]))

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
           {}))))

(re-frame/reg-event-fx
 :toggle-server
 (fn toggle-server [{db :db} _]
   (let [server (case (:server db)
                  :production :development
                  :development :production
                  :production)]
     {:dispatch [:set-page (:page db) server]})))


(re-frame/reg-event-fx
 :preload-base
  (fn preload-base [{db :db} _]
    {:http-xhrio (preload/initial-data (:server db))
     :db  (assoc db :loading? true)}))

(re-frame/reg-event-fx
 :get-schema
  (fn get-schema [{db :db} _]
    (let [server (:server db)]
      {:http-xhrio [(api/get-request server "categories" {} [:got-schema :categories])
                    (api/get-request server "currencies" {} [:got-schema :currencies])
                    (api/get-request server "paymentMethods" {} [:got-schema :payment-methods])
                    (api/get-request server "users" {} [:got-schema :users])
                    (api/get-request server "vendors" {} [:got-schema :vendors])
                    ]
       :db  (assoc db :loading? true)}
      )))

(re-frame/reg-event-fx
 :get-history
 (fn get-history [{db :db} _]
   {:http-xhrio [(api/get-request (:server db) "purchases" {} [:got-history])]}))


(re-frame/reg-event-db
 :got-schema
 (fn got-schema [db [_ section pull-response]]
   (assoc-in db [:schema section] pull-response)))

(re-frame/reg-event-db
 :got-history
 (fn got-history [db [_ pull-response]]
   (assoc db :history (sort-by :purchase/date pull-response))))

(re-frame/reg-event-db
 :edit-current-receipt
 (fn edit-current-receipt [db [_ field value]]
   (assoc-in db [:current-receipt field] value)))

(re-frame/reg-event-fx
 :submit-receipt
 (fn submit-receipt [{db :db} [_ receipt]]
   {:http-xhrio (api/post-purchase-request (:server db)
                 (assoc receipt
                        ;; [TODO]  Fixup use of Transit for Post (code location #4 for this issue)
                        :purchase/date (.toJSON (time-coerce/to-date (:purchase/date receipt)))
                        ;; [TODO]  Need better UID
                        :purchase/uid (str "UID-" (.getTime (js/Date.)) "-" (rand-int 1000))
                        :purchase/price (js/parseFloat (:purchase/price receipt))
                        ;; [TODO] temp
                        :purchase/currency "NIS")
                 [:submitted-receipt]
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

(re-frame/reg-event-db
 :process-response
 (fn process-response [db [_ response]]
   (assoc-in db [:dbg :last-response] response)))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & result]
   (prn "Got failure: " result)
   db))
