(ns receipts-client.api-client
  (:require [ajax.core :as ajax]))

(def api-root "http://localhost:8080/api/receipts-server/v1/")
(def api-timeout 5000)

(defn namespaced->str [[k v]]
  (if (and (keyword? k)
           (not (empty? (namespace k))))
    {(subs (str k) 1) v}
    {k v}))

(defn- api-request [method api params on-success]
  ;; [TODO] Fixup how we are using Transit and namespaced keywords, to avoid this ugliness
  ;; This is code location #1 for this issue
  (let [params (if (= method :get)
                 params
                 (into {} (map namespaced->str params)))]
    {:method method
     :uri (str api-root api)
     :params (if (or (= method :get)
                     (:payload params))
               params
               {:payload [params]})
     :timeout api-timeout
     ;; [TODO] Use Transit for Post too. See https://github.com/cognitect-labs/vase/issues/68
     ;; This is code location #2 for this issue
     :format (if (= method :get)
               (ajax/transit-request-format)
               (ajax/json-request-format))
     :response-format (if false
                        (ajax/json-response-format {:keywords? true})
                        (ajax/transit-response-format))
     :on-success on-success
     :on-failure [:process-failure]}))

(def get-request (partial api-request :get))
(def post-request (partial api-request :post))
(def delete-request (partial api-request :delete))

;; [TODO] Refactor this repeated code into an engine

(defn get-user-request [{:user/keys [name abbrev email] :as params} on-success]
  (get-request "user" params on-success))

(defn post-user-request [{:user/keys [name abbrev email sysAdmin? editor? consumer?] :as params} on-success]
  (post-request "user" params on-success))

(defn get-vendor-request [{:vendor/keys [name] :as params} on-success]
  (get-request "vendor" params on-success))

(defn post-vendor-request [{:vendor/keys [name description category] :as params} on-success]
  (post-request "vendor" params on-success))

(defn post-vendors-request [vendors on-success]
  ;; [TODO] Barf! Try to use Transit, instead of JSON to clean up this sty
  ;; This is code location #3 for this issue
  (post-request "vendor" {:payload
                          (mapv (fn [vendor]
                                  (into {} (map namespaced->str vendor)))
                                vendors)}
                on-success))

(defn get-category-request [{:category/keys [name] :as params} on-success]
  (get-request "category" params on-success))

(defn post-category-request [{:category/keys [name description] :as params} on-success]
  (post-request "category" params on-success))

(defn get-payment-method-request [{:paymentMethod/keys [name abbrev] :as params} on-success]
  (get-request "paymentMethod" params on-success))

(defn post-payment-method-request [{:paymentMethod/keys [name abbrev] :as params} on-success]
  (post-request "paymentMethod" params on-success))

(defn get-currency-request [{:currency/keys [name abbrev] :as params} on-success]
  (get-request "currency" params on-success))

(defn post-currency-request [{:currency/keys [name abbrev] :as params} on-success]
  (post-request "currency" params on-success))

(defn get-purchase-request [{:purchase/keys
                             [uid price currency category vendor paid-by date comment for-whom] :as params}
                            on-success]
  (get-request "purchase" params on-success))

(defn post-purchase-request [{:purchase/keys
                              [uid price currency category vendor paid-by date comment for-whom] :as params}
                             on-success]
  (post-request "purchase" params on-success))
