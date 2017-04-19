(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [cljs-time.core :as time]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]
             [receipts-client.preload :as preload]))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-fx
 :set-active-panel
 (fn [{db :db} [_ active-panel]]
   (into {:db (assoc db :active-panel active-panel)}
         (case active-panel
           :history {:dispatch [:get-history]}
           :home {:dispatch [:submitted-receipt]}
           {}))))


(re-frame/reg-event-fx
 :preload-base
  (fn preload-base [{db :db} _]
    {:http-xhrio (preload/initial-data)
     :db  (assoc db :loading? true)}))

(re-frame/reg-event-fx
 :get-schema
  (fn get-schema [{db :db} _]
    {:http-xhrio [(api/get-request "categories" {} [:got-schema :categories])
                  (api/get-request "currencies" {} [:got-schema :currencies])
                  (api/get-request "paymentMethods" {} [:got-schema :payment-methods])
                  (api/get-request "users" {} [:got-schema :users])
                  (api/get-request "vendors" {} [:got-schema :vendors])
                  ]
     :db  (assoc db :loading? true)}))

(re-frame/reg-event-fx
 :get-history
 (fn get-history [{db :db} _]
   {:http-xhrio [(api/get-request "purchases" {} [:got-history])]}))


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
   {:http-xhrio (api/post-purchase-request
                 (assoc receipt
                        ;; [TODO]  Fixup use of Transit for Post (code location #4 for this issue)
                        :purchase/date (.toJSON (:purchase/date receipt))
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
