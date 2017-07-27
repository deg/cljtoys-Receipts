(ns receipts.specs
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as gen]))

;; [TODO] Move to utils
(defn validate
  "Like s/valid?, but show the error like s/assert"
  [spec x]
  (or (s/valid? spec x)
      (s/explain spec x)))

(s/def ::non-empty-string (s/and string? not-empty))

(s/def ::event-vector (s/cat :event keyword? :params (s/* any?)))

(s/def ::api #{"category" "categories" "currency" "currencies" "purchase" "source" "sources" "user" "users" "vendor" "vendors"})
(s/def ::entity-name #{"user" "source" "currency" "category" "vendor"})
(s/def ::section #{:users :sources :currencies :categories :vendors})


(def purchase-keys #{:purchase/source
                     :purchase/date
                     :purchase/price
                     :purchase/category
                     :purchase/consumer
                     :purchase/vendor
                     :purchase/comment})
(s/def ::purchase-keys purchase-keys)

(s/def ::purchase (s/keys :req (remove #{:purchase/comment}
                                       (into [:db/id :purchase/uid :purchase/currency]
                                             purchase-keys))
                          :opt [:purchase/comment]))
(s/def :db/id int?)
(s/def :purchase/uid ::non-empty-string)
(s/def :purchase/source ::non-empty-string)
(s/def :purchase/date (s/inst-in #inst "1900-01-01" #inst "2099-12-31"))
(s/def :purchase/currency ::non-empty-string)
(s/def :purchase/price float)
(s/def :purchase/category ::non-empty-string)
(s/def :purchase/vendor ::non-empty-string)
(s/def :purchase/comment string?)
(s/def :purchase/consumer (s/coll-of ::non-empty-string))

(s/def ::category (s/keys :req [:category/name :category/description]))
(s/def :category/name string?)
(s/def :category/description string?)

(s/def ::currency (s/keys :req [:currency/name :currency/abbrev]))
(s/def :currency/name string?)
(s/def :currency/abbrev string?)

(s/def ::source (s/keys :req [:source/name :source/abbrev]))
(s/def :source/name string?)
(s/def :source/abbrev string?)

(s/def ::vendor (s/keys :req [:vendor/name :vendor/category]))
(s/def :vendor/name string?)
(s/def :vendor/category vector?)

(s/def ::user (s/keys :req [:user/name
                            :user/abbrev]
                      :opt [:user/email
                            :user/isConsumer
                            :user/isEditor
                            :user/isAdmin]))
(s/def :user/name string?)
(s/def :user/abbrev string?)
(s/def :user/email string?)
(s/def :user/isConsumer boolean?)
(s/def :user/isEditor boolean?)
(s/def :user/isAdmin boolean?)

;; [TODO] LOOK AT defmulti in https://clojure.org/guides/spec
(s/def ::entity (s/or :categories ::category
                      :currencies ::currency
                      :sources ::source
                      :vendor ::vendor
                      :users ::user))

(s/def ::auth-level keyword?)

(s/def :receipts-server/context (s/keys :req-un [::request ::response]))

(s/def :receipts-client/page keyword?)

(s/def ::db (s/keys :req-un [::name ::server] :opt-un [::about ::credentials ::history ::schema]))

(s/def :user/email string?)
(s/def :user/token string?)
(s/def :user/credentials (s/keys :req [:user/email :user/token]))
