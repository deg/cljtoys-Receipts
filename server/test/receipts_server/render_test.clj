(ns receipts-server.render-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [io.pedestal.http :as http]
   [io.pedestal.test :refer :all]
   [receipts-server.render :as render]
   [receipts-server.specs :as specs]
   [receipts-server.test-helper :as helper]))


(deftest one-purchase
  (let [purchase {:purchase/uid "xy123zabc",
                  :db/id 2917314159,
                  :purchase/consumer ["Family" "Harry"],
                  :purchase/date #inst "2017-01-23T12:34:56.567-00:00",
                  :purchase/currency "USD",
                  :purchase/price 550.95,
                  :purchase/source "MC 1234",
                  :purchase/category "Vacation",
                  :purchase/vendor "Westin",
                  :purchase/comment "Northern Rockies"}]
    (is (= (str "Source,Date,Amount,Category,Vendor,Comment,Consumer,Currency\n"
                "MC 1234,23Jan2017,550.95,Vacation,Westin,Northern Rockies,\"Family, Harry\",USD\n")
           (render/csv-purchases (vector purchase))))))

(deftest gen-purchases
  (let [num 10
        header "Source,Date,Amount,Category,Vendor,Comment,Consumer,Currency"
        header-fields (str/split header #",")
        purchases (gen/sample (s/gen :receipts/purchase) num)
        csv-lines (str/split-lines (render/csv-purchases purchases))]
    (is (= (first csv-lines) header))
    (is (= (count (rest csv-lines)) num))
    (run! (fn [line]
            (let [fields
                  ;; Split on unquoted commas. Hat tip to
                  ;; https://stackoverflow.com/questions/632475/regex-to-pick-commas-outside-of-quotes
                  (str/split line #"(,)(?=(?:[^\"]|[\"][^\"]*\")*$)")]
              (is (= (count fields)
                     (count header-fields)))))
          (rest csv-lines))))
