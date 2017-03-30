(ns receipts-client.core-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [receipts-client.core :as core]))

(deftest fake-test
  (testing "fake description"
    (is (= 1 2))))
