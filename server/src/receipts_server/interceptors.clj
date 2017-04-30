;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-server.interceptors
  (:require
   [clj-time.coerce :as time-coerce]
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojure-csv.core :as csv]
   [clojure.instant :as instant]
   [clojure.string :as str]
   [io.pedestal.interceptor :as i]
   [io.pedestal.log :as log]))

;; interceptor to convert RFC3339 datetime format to Datomic friendly
;; datetime object.
;; From https://github.com/cognitect-labs/vase/blob/master/samples/petstore-full/src/petstore_full/interceptors.clj
(def date-conversion
  (i/interceptor
   {:name ::date-conversion
    :enter (fn [context]
             (let [payloads (get-in context [:request :json-params :payload])
                   payloads (map (fn [m] (if (:purchase/date m)
                                           (update m :purchase/date instant/read-instant-date)
                                           m))
                                 payloads)]
               (assoc-in context [:request :json-params :payload] payloads)))}))

;;; Deal with the fact that Javascript will send integral floats (e.g. 2.0) as integers
(def float-conversion
  (i/interceptor
   {:name ::float-conversion
    :enter (fn [context]
             (let [payloads (get-in context [:request :json-params :payload])
                   payloads (map (fn [m] (if (:purchase/price m)
                                           (update m :purchase/price float)
                                           m))
                                 payloads)]
               (assoc-in context [:request :json-params :payload] payloads)))}))

(def date-format (time-format/formatter "ddMMMyyyy"))

;;; Convert series of purchases into a single CSV string suitable, e.g., for MS Excel.
(defn csv [purchases]
  (let [cells (into [["Paid By" "Date" "Amount" "Category" "Vendor" "Comment" "Currency" "For Whom"]]
                    (mapv (fn [{:purchase/keys [paymentMethod date currency price category vendor comment forWhom]}]
                            (vector paymentMethod
                                    (time-format/unparse date-format(time-coerce/to-date-time date))
                                    (str price)
                                    category
                                    vendor
                                    comment
                                    currency
                                    (str/join ", " forWhom)))
                          (sort-by :purchase/date purchases)))]
    (csv/write-csv cells)))

;;; Interceptor to generate CSV
;;; Note battle scars:
;;; 1) This must happen in the :leave. Though under-documented, it does not work to set context's
;;;    :response in an :enter.  See terminator-injector in
;;;    https://github.com/pedestal/pedestal/blob/master/service/src/io/pedestal/http/impl/servlet_interceptor.clj#L364-L377
;;; 2) transit+json format must be valid json. So, cannot send a bare string. Therefore, the {:cvs ...} wrapper
(def csv-response
  (i/interceptor
   {:name ::csv-response
    :leave (fn [context]
             (update-in context [:response] assoc
                        :body    {:csv (csv (:csv/raw context))}
                        :headers {}
                        :status  200))}))

