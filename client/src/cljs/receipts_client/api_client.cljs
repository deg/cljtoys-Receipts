;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.api-client
  (:require [ajax.core :as ajax]))

(def production-api-root "http://lightsail-1.degel.com:8080/api/receipts-server/v1/")
(def development-api-root "http://localhost:8080/api/receipts-server/v1/")
(defn api-root [server]
  (case server
    :production production-api-root
    :development development-api-root
    nil))
(def api-timeout 5000)

;;; [TODO] Belongs in forthcoming utils library. Cribbed from photovu/src/cljc
(defn assoc-if
  "Like assoc, but takes pairs as a map, and only assocs non-nil values"
  [m kvs]
  (into m (remove (comp nil? val) kvs)))

;;; [TODO] Barf! Try to use Transit, instead of JSON to clean up this sty
(defn namespaced->str [[k v]]
  {(if (and (keyword? k) (not (empty? (namespace k))))
     (subs (str k) 1)
     k)
   v})

(defn string-keyed [mapp]
  (into {} (map namespaced->str mapp)))

(defn- api-request [{:keys [method server api params response-format timeout on-success on-failure]
                     :or {response-format (ajax/transit-response-format)
                          timeout api-timeout
                          on-failure [:process-failure]}}]
  ;; [TODO] Fixup how we are using Transit and namespaced keywords, to avoid this ugliness
  ;; This is code location #1 for this issue
  (let [params (if (= method :get)
                 params
                 (string-keyed params))]
    {:method method
     :uri (str (api-root server) api)
     :params (if (or (= method :get) (:payload params))
               params
               {:payload [params]})
     :timeout timeout
     ;; [TODO] Use Transit for Post too. See https://github.com/cognitect-labs/vase/issues/68
     ;; This is code location #2 for this issue
     :format (if (= method :get)
               (ajax/transit-request-format)
               (ajax/json-request-format))
     :response-format (if (string? response-format) {:content-type response-format} response-format)
     :on-success on-success
     :on-failure on-failure}))

(defn get-request [{:keys [server api params on-success] :as request-params}]
  (api-request (assoc request-params :method :get)))
(defn post-request  [{:keys [server api params on-success] :as request-params}]
  (api-request (assoc request-params :method :post)))
(defn delete-request  [{:keys [server api params on-success] :as request-params}]
  (api-request (assoc request-params :method :delete)))


