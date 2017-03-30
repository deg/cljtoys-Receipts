(ns receipts-client.runner
    (:require [doo.runner :refer-macros [doo-tests]]
              [receipts-client.core-test]))

(doo-tests 'receipts-client.core-test)
