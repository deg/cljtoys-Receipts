;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.utils)

(defn console-dir [label object]
  (js/console.log label)
  (js/console.dir object))

(defn assoc-if
  "Like assoc, but takes pairs as a map, and only assocs non-nil values"
  [m kvs]
  (into m (remove (comp nil? val) kvs)))



(defn get-at
  "Coll is a sequence of maps.
   Find the element of coll for which id-key's value is id.
   Get the element or, if key is supplied, return element's key."
  ([coll id-key id]
   (-> #(= (id-key %) id)
       (filter coll)
       first))
  ([coll id-key id key]
   (key (get-at coll id-key id))))

;; HT: https://stackoverflow.com/questions/8641305/find-index-of-an-element-matching-a-predicate-in-clojure
(defn indices [pred coll]
  (keep-indexed #(when (pred %2) %1)
                coll))

(defn update-at
  "Coll is a sequence of maps.
   Update the element of coll for which id-key's value is id."
  [coll id-key id updater & update-args]
  (let [index (first (indices #(= (id-key %) id) coll))]
    (if index
      (apply update coll index updater update-args)
      coll)))

;; HT: to https://stackoverflow.com/questions/32467299/clojurescript-convert-arbitrary-javascript-object-to-clojure-script-map
(defn jsx->clj [x]
  (into {} (for [k (.keys js/Object x)] [k (aget x k)])))

(defn goog-date? [x]
  (if-let [date (.-date x)]
    (inst? date)
    false))
