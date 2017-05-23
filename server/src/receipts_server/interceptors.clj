;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-server.interceptors
  (:require
   [clojure.instant :as instant]
   [datomic.api :as d]
   [io.pedestal.interceptor :as i]
   [io.pedestal.interceptor.chain :as chain]
   [io.pedestal.log :as log])
  (:import [javax.crypto Cipher SecretKey]
           [javax.crypto.spec SecretKeySpec]
           [java.util Base64]))

(defn update-one-field
  "Payloads is a collection of maps. In each map, update field with fcn."
  [fcn field payloads]
  (map #(if (field %)
          (update % field fcn)
          %)
       payloads))

(defn update-field-to-field
  "Payloads is a collection of maps. In each map, assoc outfield with fcn of in-field.
  That is (update-field-to-field #(* 3 %) :in :out [{:in 1} {:in 2}]) => [{:out 3} {:out 6}]"
  [fcn in-field out-field payloads]
  (map #(if (in-field %)
          (dissoc (assoc % out-field (-> % in-field fcn))
                  in-field)
          %)
       payloads))

(defn update-field [fcn field-or-fields payload]
  (if (map? field-or-fields)
    (update-field-to-field fcn (:in field-or-fields) (:out field-or-fields) payload)
    (update-one-field fcn field-or-fields payload)))

(defn payload-updater [updater]
  (fn [context]
    (update-in context [:request :json-params :payload] updater)))

(defn body-updater [updater]
  (fn [context]
    (update-in context [:response :body] updater)))

;;; Parse incoming date string
;;; Originally based on
;;; https://github.com/cognitect-labs/vase/blob/master/samples/petstore-full/src/petstore_full/interceptors.clj
(defn date-updater [field]
  (partial update-field instant/read-instant-date field))

;;; Deal with the fact that Javascript will send integral floats (e.g. 2.0) as integers
(defn float-updater [field]
  (partial update-field float field))

(defn project-version []
  (-> (clojure.java.io/resource "project.clj")
      slurp
      read-string
      (nth 2)))

(defn dependency-versions []
  (->> (clojure.java.io/resource "project.clj")
       slurp
       read-string
       (drop 3)
       (partition 2)
       (map vec)
       (into {})
       :dependencies
       (map (partial take 2))))


;;; ================================================================

;;; Half-baked authentication, based on
;;; https://github.com/cognitect-labs/vase/blob/master/samples/petstore-full/src/petstore_full/interceptors.clj

;; setup for encrypt/decrypt
;; [TODO] Get from env
(def secret-key "It Is Secret Key")  ;; exactly 16 bytes
(def ^SecretKey skey (SecretKeySpec. (.getBytes secret-key "UTF-8") "AES"))
(def ^Cipher encryptor (doto (Cipher/getInstance "AES") (.init Cipher/ENCRYPT_MODE skey)))
(def ^Cipher decryptor (doto (Cipher/getInstance "AES") (.init Cipher/DECRYPT_MODE skey)))

(defn encrypt
  "Encrypts input String"
  [s]
  (let [bytes (.doFinal encryptor (.getBytes s "UTF-8"))]
    (.encodeToString (Base64/getEncoder) bytes)))

(defn encrypt-updater [field]
  (partial update-field encrypt field))

(defn decrypt
  "Decrypts input String"
  [s]
  (let [bytes (.decode (Base64/getDecoder) s)
        bytes (.doFinal decryptor bytes)]
    (String. bytes)))

(defn decrypt-updater [field]
  (partial update-field decrypt field))

(defn shroud
  "Replace string with anodyne value"
  [_]
  "********")

(defn shroud-updater [field]
  (partial update-field shroud field))

;;; [TODO] Alert: ad-hoc security.
;;; I'm uncomfortable about releasing the stored encrypted passwords into the wild. So,
;;; for an added bit of obscurity release as "tokens" after an additional round of
;;; encryption.  Barf?
;;; Re-examine this once we have client-side encryption working.

(defn password->token [password]
  (-> password encrypt encrypt))

(defn encrypted->token [encrypted]
  (encrypt encrypted))

(defn terminate-with-error [context status body]
  (-> context
      (assoc-in [:response :body] body)
      (assoc-in [:response :status] status)
      chain/terminate))

(def validate-login
  (i/interceptor
   {:name :validate-login
    :leave (fn [context]
             (let [body (get-in context [:response :body])]
               (if (next body)
                 (terminate-with-error context 500 {:error (str "Duplicated email: " body)})
                 (let [user (first body)
                       old-encrypted (:user/passwordEncrypted user)
                       encrypted (encrypt (get-in context [:request :params :password]))
                       editor? (:user/isEditor user)]
                   (if (and editor? (= encrypted old-encrypted))
                     (assoc-in context [:response :body]
                               {:user/credentials
                                {:user/email (:user/email user)
                                 :user/token (encrypted->token (:user/passwordEncrypted user))}})
                     (terminate-with-error context 401 {:error "Login failed"}))))))}))

(defn credentials [context]
  (let [request (:request context)
        {:keys [db request-method params json-params]} request
        get? (= request-method :get)
        email (if get? (:email params) (-> json-params :credentials :email))
        token (if get? (:token params) (-> json-params :credentials :token))]
    [email token]))

(def authorization-query
  '[:find ?passwordEncrypted ?authorized
    :in $ ?email ?level
    :where
    [?e :user/email ?email]
    [?e :user/passwordEncrypted ?passwordEncrypted]
    [?e ?level ?authorized]])

(defn authorize
  "Authorize access to restricted data, at level :user/isEditor or :user/isAdmin"
  [context level]
  (let [[email token] (credentials context)
        db (get-in context [:request :db])
        [stored-encrypted-password authorized] (when email
                                       (first
                                        (d/q authorization-query db email level)))]
    (if (and authorized
             stored-encrypted-password
             (= token (encrypted->token stored-encrypted-password)))
      context
      (terminate-with-error context 401 {:error "User not authorized"}))))

(def auth-editor
  (i/interceptor
   {:name ::auth-editor
    :enter (fn [context] (authorize context :user/isEditor))}))

(def auth-admin
  (i/interceptor
   {:name ::auth-admin
    :enter (fn [context] (authorize context :user/isAdmin))}))

(def filter-users
  (i/interceptor
   {:name ::filter-users
    :leave (fn [context]
             (let [[email _] (credentials context)]
               (update-in context [:response :body]
                          (fn [users]
                            (filter #(or (:user/isConsumer %)
                                         (= email (:user/email %)))
                                    users)))))}))

