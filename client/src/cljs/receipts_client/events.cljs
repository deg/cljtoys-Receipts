(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]
             [receipts-client.preload :as preload]))

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))


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
 :prepare-page
 (fn prepare_page [{db :db} [_ page]]
   (case page
     "history"
     {:dispatch [:get-history]}
     {:db db})))


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
                 [:process-response])
    :db (assoc db
               :previous-receipt receipt)}))

(re-frame/reg-event-db
 :process-response
 (fn process-response [db [_ response]]
   (assoc-in db [:dbg :last-response] response)))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & result]
   (prn "Got failure: " result)
   db))
