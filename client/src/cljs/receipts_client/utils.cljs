;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.utils)


(defn filter-by [keyfn pred coll]
  (filter #(pred (keyfn %)))
  )

;;; coll is a sequence of maps.
;;; Find the element of coll for whose id-key value is id
;;; Return the value at key

(defn get-at [coll id-key id key]
  (-> #(= (id-key %) id)
      (filter coll)
      first
      key))
