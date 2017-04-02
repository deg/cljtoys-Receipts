(ns receipts-client.api-client
  (:require [ajax.core :as ajax]))

(def api-root "http://localhost:8080/api/receipts-server/v1/")
(def api-timeout 5000)

(defn api-request [method api params on-success]
  {:method method
   :uri (str api-root api)
   :params (if (or (= method :get)
                   (:payload params))
             params
             {:payload [params]})
   :timeout api-timeout
   :format (if (= method :get)
             (ajax/transit-request-format)
             (ajax/json-request-format))
   :response-format (if false
                      (ajax/json-response-format {:keywords? true})
                      (ajax/transit-response-format))
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

;;; [TODO] Temp, until we've shared the namespaces with the client

(defn- User-request [{:keys [name abbrev email sysAdmin? editor? consumer?]}]
  (assoc-if {}
    {"receipts.user/name" name
     "receipts.user/abbrev" abbrev
     "receipts.user/email" email
     "receipts.user/isSysAdmin" sysAdmin?
     "receipts.user/isEditor" editor?
     "receipts.user/isConsumer" consumer?}))

(defn- Category-request [{:keys [name description]}]
  (assoc-if {}
    {"receipts.category/name" name
     "receipts.category/description" description}))

(defn- Vendor-request [{:keys [name description category]}]
  (assoc-if {}
    {"receipts.vendor/name" name
     "receipts.vendor/description" description
     "receipts.vendor/category" category}))

(defn- Payment-method-request [{:keys [name abbrev]}]
  (assoc-if {}
    {"receipts.paymentMethod/name" name
     "receipts.paymentMethod/abbrev" abbrev}))

(defn- Currency-request [{:keys [name abbrev]}]
  (assoc-if {}
    {"receipts.currency/name" name
     "receipts.currency/abbrev" abbrev}))

(defn- Purchase-request [{:keys [uid price currency category vendor paid-by date comment for-whom]}]
  (assoc-if {}
    {"receipts.purchase/uid" uid
     "receipts.purchase/price" price
     "receipts.purchase/currency" currency
     "receipts.purchase/category" category
     "receipts.purchase/vendor" vendor
     "receipts.purchase/paidBy" paid-by
     "receipts.purchase/date" date
     "receipts.purchase/comment" comment
     "receipts.purchase/forWhom" for-whom}))

;; [TODO] Refactor this repeated code into an engine

(defn get-user-request [{:keys [name abbrev email] :as params} on-success]
  (get-request "user" params on-success))

(defn post-user-request [{:keys [name abbrev email sysAdmin? editor? consumer?] :as params} on-success]
  (post-request "user" (User-request params) on-success))

(defn get-vendor-request [{:keys [name] :as params} on-success]
  (get-request "vendor" params on-success))

(defn post-vendor-request [{:keys [name description category] :as params} on-success]
  (post-request "vendor" (Vendor-request params) on-success))

(defn post-vendors-request [vendors on-success]
  (post-request "vendor" {:payload (mapv Vendor-request vendors)} on-success))

(defn get-category-request [{:keys [name] :as params} on-success]
  (get-request "category" params on-success))

(defn post-category-request [{:keys [name description] :as params} on-success]
  (post-request "category" (Category-request params) on-success))

(defn get-payment-method-request [{:keys [name abbrev] :as params} on-success]
  (get-request "paymentMethod" params on-success))

(defn post-payment-method-request [{:keys [name abbrev] :as params} on-success]
  (post-request "paymentMethod" (Payment-method-request params) on-success))

(defn get-currency-request [{:keys [name abbrev] :as params} on-success]
  (get-request "currency" params on-success))

(defn post-currency-request [{:keys [name abbrev] :as params} on-success]
  (post-request "currency" (Currency-request params) on-success))

(defn get-purchase-request [{:keys [uid price currency category vendor paid-by date comment for-whom] :as params} on-success]
  (get-request "purchase" params on-success))

(defn post-purchase-request [{:keys [uid price currency category vendor paid-by date comment for-whom] :as params} on-success]
  (post-request "purchase" (Purchase-request params) on-success))
