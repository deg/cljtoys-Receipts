(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.db :as db]))

(def api-root "http://localhost:8080/api/receipts-server/v1/")
(def api-timeout 5000)

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(defn api-request [method api params on-success]
  {:method method
   :uri (str api-root api)
   :params (if (= method :get) params {:payload [params]})
   :timeout api-timeout
   :format (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success on-success
   :on-failure [:process-failure]})

;; [TODO] Belongs in forthcoming utils library. Cribbed from photovu/src/cljc
(defn assoc-if
  "Like assoc, but takes pairs as a map, and only assocs non-nil values"
  [m kvs]
  (into m (remove (comp nil? val) kvs)))

(def get-request (partial api-request :get))
(def post-request (partial api-request :post))
(def delete-request (partial api-request :delete))

(defn get-user-request [{:keys [name abbrev email] :as params} on-success]
  (get-request "user" (assoc-if {} params) on-success))

(defn new-user-request [{:keys [name abbrev email editor?] :or {editor? false}} on-success]
  (post-request "user"
                (assoc-if {}
                  {"receipts.user/name" name
                   "receipts.user/abbrev" abbrev
                   "receipts.user/email" email
                   "receipts.user/isEditor" editor?})
                on-success))

(defn update-user-request [id {:keys [name abbrev email editor?] :or {editor? false}} on-success]
  (prn "Updating with"                 (assoc-if {"db/id" id}
                                         {"receipts.user/name" name
                                          "receipts.user/abbrev" abbrev
                                          "receipts.user/email" email
                                          "receipts.user/isEditor" editor?}))
  (post-request "user"
                (assoc-if {"db/id" id}
                  {"receipts.user/name" name
                   "receipts.user/abbrev" abbrev
                   "receipts.user/email" email
                   "receipts.user/isEditor" editor?})
                on-success))

(defn upsert-user-request [{:keys [name abbrev email editor?] :or {editor? false} :as params}]
  (get-user-request params [:upsert-user-got-user params]))

(re-frame/reg-event-fx
 :upsert-user-got-user
 (fn upsert-user-got-user
   [{db :db} [_ new-params [[users]]]]
   (let [{id :db/id} (first users)
         request (if id
                   (update-user-request id new-params [:process-response])
                   (new-user-request new-params [:process-response]))]
     {:http-xhrio [request]
      :db db})))

(re-frame/reg-event-fx
 :toy-startup
  (fn
    [{db :db} stuff]
    {:http-xhrio [(upsert-user-request
                   {:name "David Goldfarb"    :abbrev "D" :email "deg@degel.com"                :editor? true})
                  (upsert-user-request
                   {:name "Heidi Brun"        :abbrev "H" :email "hmb@goldfarb-family.com"      :editor? true})
                  (upsert-user-request
                   {:name "Aviva Goldfarb"    :abbrev "A" :email "aviva@goldfarb-family.com"    :editor? true})
                  (upsert-user-request
                   {:name "Shoshana Goldfarb" :abbrev "S" :email "shoshana@goldfarb-family.com" :editor? true})
                  (upsert-user-request {:name "Netzach Menashe" :abbrev "Netzach"  :editor? false})
                  (upsert-user-request {:name "HBA"             :abbrev "HBA"      :editor? false})
                  (upsert-user-request {:name "Degel"           :abbrev "Degel"    :editor? false})
                  (get-request "users" {} [:process-response])
                  ]
     :db  (assoc db :loading? true)}))

(re-frame/reg-event-db
 :process-response
 (fn process-response [db result]
   (prn "Got response: " result)
   db))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & result]
   (prn "Got failure: " result)
   db))
