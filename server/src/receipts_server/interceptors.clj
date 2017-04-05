(ns receipts-server.interceptors
  (:require [clojure.instant :as instant]
            [io.pedestal.interceptor :as i]))

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
