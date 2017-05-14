;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.utils)


(defn assoc-if
  "Like assoc, but takes pairs as a map, and only assocs non-nil values"
  [m kvs]
  (into m (remove (comp nil? val) kvs)))



(defn get-at
  "Coll is a sequence of maps.
  Find the element of coll for whose id-key value is id.
  Return the value at key."
  ([coll id-key id]
   (-> #(= (id-key %) id)
       (filter coll)
       first))
  ([coll id-key id key]
   (key (get-at coll id-key id))))
