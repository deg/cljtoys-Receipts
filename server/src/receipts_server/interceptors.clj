;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-server.interceptors
  (:require
   [clojure.instant :as instant]
   [io.pedestal.interceptor :as i]
   [io.pedestal.log :as log]))

(defn update-field [f field payloads]
  (map #(if (field %)
          (update % field f)
          %)
       payloads))

;;; Parse incoming date string
;;; Originally based on
;;; https://github.com/cognitect-labs/vase/blob/master/samples/petstore-full/src/petstore_full/interceptors.clj
(defn date-updater [field]
  (partial update-field instant/read-instant-date field))

;;; Deal with the fact that Javascript will send integral floats (e.g. 2.0) as integers
(defn float-updater [field]
  (partial update-field float field))

