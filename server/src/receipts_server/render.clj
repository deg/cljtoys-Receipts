;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-server.render
  (:require
   [clj-time.coerce :as time-coerce]
   [clj-time.format :as time-format]
   [clojure-csv.core :as csv]
   [clojure.string :as str]
   [io.pedestal.log :as log]))

(def date-format (time-format/formatter "ddMMMyyyy"))

;;; Convert series of purchases into a single CSV string suitable, e.g., for MS Excel.
(defn csv-purchases [purchases]
  (let [cells (into [["Paid By" "Date" "Amount" "Category" "Vendor" "Comment" "Currency" "For Whom"]]
                    (mapv (fn [{:purchase/keys [paymentMethod date currency price category vendor comment forWhom]}]
                            (vector paymentMethod
                                    (time-format/unparse date-format (time-coerce/to-date-time date))
                                    (str price)
                                    category
                                    vendor
                                    (or comment "")
                                    currency
                                    (str/join ", " forWhom)))
                          (sort-by :purchase/date purchases)))]
    (csv/write-csv cells)))

