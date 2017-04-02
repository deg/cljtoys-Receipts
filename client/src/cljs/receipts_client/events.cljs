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
 :get-base
  (fn get-base [{db :db} _]
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


(defn response-from-pull [pull-response]
  (let [relevant (first pull-response)
        cruft1 (next pull-response)
        cruft2 (not-empty (remove nil? (map next relevant)))]
    (when (or cruft1 cruft2)
      (prn "ERROR: Unexpected pull response: " pull-response))
    (mapv first relevant)))

(re-frame/reg-event-db
 :got-schema
 (fn got-schema [db [_ section pull-response]]
   (assoc-in db [:schema section] (response-from-pull pull-response))))

(re-frame/reg-event-db
 :got-history
 (fn got-history [db [_ pull-response]]
   (let [response (response-from-pull pull-response)]
     (assoc db :history (sort-by :receipts.purchase/date response)))))

(re-frame/reg-event-db
 :edit-current-receipt
 (fn edit-current-receipt [db [_ field value]]
   (assoc-in db [:current-receipt field] value)))

(re-frame/reg-event-db
 :process-response
 (fn process-response [db [_ response]]
   (assoc-in db [:dbg :last-response] response)))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & result]
   (prn "Got failure: " result)
   db))
