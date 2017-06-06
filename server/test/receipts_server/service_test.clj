(ns receipts-server.service-test
  (:require [clojure.test :refer :all]
            [io.pedestal.test :refer :all]
            [io.pedestal.http :as http]
            [receipts-server.test-helper :as helper]
            [receipts-server.service :as service]))

;; To test your service, call `(helper/service` to get a new service instance.
;; If you need a constant service over multiple calls, use `(helper/with-service ...)
;; All generated services will have randomized, consistent in-memory Datomic DBs
;; if required by the service
;;
;; `helper` also contains shorthands for common `response-for` patterns,
;; like GET, POST, post-json, post-edn, and others

(deftest home-page-test
  (let [{:keys [body header]} (helper/GET "/")]
    (is (= "Not Found" body))
    (is (nil? header))))


(deftest random-page-test
  (let [{:keys [body header]} (helper/GET "foo-bar.html")]
    (is (= "Not Found" body))
    (is (nil? header))))

