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
  (let [cells (into [["Source" "Date" "Amount" "Category" "Vendor" "Comment" "Consumer" "Currency"]]
                    (mapv (fn [{:purchase/keys [source date currency price category vendor comment consumer]}]
                            (vector source
                                    (time-format/unparse date-format (time-coerce/to-date-time date))
                                    (str price)
                                    category
                                    vendor
                                    (or comment "")
                                    (str/join ", " consumer)
                                    currency))
                          (sort-by :purchase/date purchases)))]
    (csv/write-csv cells)))

