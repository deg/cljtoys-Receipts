;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.views
  (:require
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [cljs.tools.reader :as reader]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [receipts-client.routes :as routes]
   [receipts-client.utils :as utils]
   [receipts.specs :as specs]
   [sodium.chars :as chars]
   [sodium.re-utils :refer [<sub >evt]]
   [sodium.core :as na]
   [sodium.extensions :as nax]
   [struct.core :as struct]))


(defn login-panel []
  (let [email (reagent/atom "")
        password (reagent/atom "")]
    (fn []
      [na/form {}
       (nax/panel-header "Login")
       [na/form-input {:inline? true
                       :required? true
                       :label "Email"
                       :placeholder "foo@gmail.com"
                       :type "email"
                       :on-change (na/>atom email)}]
       [na/form-input {:inline? true
                       :required? true
                       :label "Password"
                       :placeholder "shhhh..."
                       :type "password"
                       :on-change (na/>atom password)}]
       [na/form-button {:on-click (na/>event [:login @email @password])
                        :content "Login"
                        :data-tooltip "Login to server"
                        :positive? true}]])))

(defn unavailable []
  [:div [:em "(Unavailable)"]])

;;; See https://funcool.github.io/struct/latest/
;;; [TODO] Replace with spec, asap.
(def complete-receipt
  {:purchase/source [[struct/required :message "'Source' missing"] struct/string]
   :purchase/date [[struct/required :message "Please specify date"] struct/positive]
   :purchase/price [[struct/required :message "Please specify amount"] struct/positive]
   :purchase/category [[struct/required :message  "Category missing"] struct/string]
   :purchase/currency [[struct/required :message  "Currency missing"] struct/string]
   :purchase/vendor [[struct/required :message  "Choose vendor in category"] struct/string]
   ;; [TODO] Catch when consumer is clicked, then unclicked back to emptyp (leaves empty set, rather than null)
   :purchase/consumer [[struct/required :message  "Specify user(s) of this purchase"] struct/set]})

(defn validate-receipt [receipt]
  (struct/validate receipt complete-receipt))

(defn valid-receipt? [receipt]
  (struct/valid? receipt complete-receipt))

(defn input-text [& {:keys [receipt field-key]}]
  [na/input {:type "text"
             :default-value (or (field-key receipt) "")
             :on-change (na/>event [:edit-current-receipt field-key])}])

(defn input-currency [& {:keys [receipt field-key]}]
  [na/input {:type "number"
             :step "0.01"
             :value (or (field-key receipt) "")
             :on-change (na/>event [:edit-current-receipt field-key])}])

(def ndate-format (time-format/formatter "yyyy-MM-dd"))

(defn date [& {:keys [receipt subs-key field-key]}]
  [na/input {:type "date"
             :value (time-format/unparse ndate-format
                                         (time-coerce/to-date-time (field-key receipt)))
             :on-change (na/>event [:edit-current-receipt field-key] (time/now) time-coerce/to-date)}])

(defn selection [& {:keys [receipt subs-key field-key schema-id-key schema-label-key filter-fn multiple?]}]
  (let [options (na/dropdown-list (if filter-fn
                                    (filter filter-fn (<sub [subs-key]))
                                    (<sub [subs-key]))
                                  schema-id-key (or schema-label-key schema-id-key))]
    [na/dropdown {:multiple? multiple?
                  :value (or (field-key receipt) (if multiple? #{} ""))
                  :options options
                  :button? true
                  :on-change (na/>event [:edit-current-receipt field-key]
                                        nil
                                        (if multiple?
                                          (partial into #{})
                                          identity))}]))

(defn currency-symbol [abbrev]
  (case abbrev
    "USD" "$"
    "EU"  chars/euro
    "GBP" chars/gb-pound
    "NIS" chars/new-israeli-shekel
    "?"))


(defn receipt-page []
  (let [receipt (<sub [:current-receipt])
        validation-errors (first (validate-receipt receipt))]
    [na/form {}
     (nax/labelled-field
      :label "Source:"
      :inline? true
      :field-key :purchase/source
      :errors  validation-errors
      :content (selection
                :receipt receipt
                :subs-key :sources
                :field-key :purchase/source
                :schema-id-key :source/abbrev
                :schema-label-key :source/name))

     (nax/labelled-field
      :label "Date:"
      :inline? true
      :field-key :purchase/date
      :errors validation-errors
      :content (date
                :receipt receipt
                :field-key :purchase/date))
     (nax/labelled-field
      :label "Price:"
      :inline? true
      :field-key :purchase/price
      :errors validation-errors
      :content (input-currency
                :receipt receipt
                :subs-key :prices
                :field-key :purchase/price))
     (nax/labelled-field
      :label "Currency:"
      :inline? true
      :field-key :purchase/currency
      :errors validation-errors
      :content (selection
                :receipt receipt
                :subs-key :currencies
                :field-key :purchase/currency
                :schema-id-key :currency/abbrev
                :schema-label-key #(str (-> % :currency/abbrev currency-symbol) ": "  (:currency/name %))))

     (nax/labelled-field
      :label "Category:"
      :inline? true
      :field-key :purchase/category
      :errors validation-errors
      :content (selection
                :receipt receipt
                :subs-key :categories
                :field-key :purchase/category
                :schema-id-key :category/name))
     (nax/labelled-field
      :label "Vendors:"
      :inline? true
      :field-key :purchase/vendor
      :errors validation-errors
      :content (selection
                :receipt receipt
                :subs-key :vendors
                :field-key :purchase/vendor
                :schema-id-key :vendor/name
                :filter-fn #(some #{(:purchase/category receipt)} (:vendor/category %))))
     (nax/labelled-field
      :label "Comment:"
      :inline? true
      :field-key :purchase/comment
      :errors validation-errors
      :content (input-text
                :receipt receipt
                :field-key :purchase/comment))
     (nax/labelled-field
      :label "Consumer:"
      :inline? true
      :field-key :purchase/consumer
      :errors validation-errors
      :content (selection
                :receipt receipt
                :subs-key :users
                :field-key :purchase/consumer
                :filter-fn :user/isConsumer
                :schema-label-key :user/name
                :schema-id-key :user/abbrev
                :multiple? true))
     [na/form-button {:on-click (na/>event
                                 [:submit-receipt (-> receipt
                                                      (update :purchase/date time-coerce/to-date)
                                                      (update :purchase/price js/parseFloat))])
                      :content "Submit Receipt"
                      :data-tooltip "Press to record receipt"
                      :positive? true
                      :disabled? (not (valid-receipt? receipt))}]]))

(defn interstitial-page []
  [:div
   [:img {:src (str "http://thecatapi.com/api/images/get?format=src&size=small&"
                    "cacheBuster=" (str (rand)))}]
   [na/form-button {:on-click (na/>event [:next-receipt])
                    :content "Continue"
                    :data-tooltip "enter next receipt"
                    :positive? true}]])

(defn home-panel []
  (if (:user/isEditor (<sub [:user]))
    (if (<sub [:receipt-stored?])
      [:div (nax/panel-header "Receipt entered") [interstitial-page]]
      [:div (nax/panel-header "New Receipt")     [receipt-page]])
    [login-panel]))

(defn has? [entity-atom field]
  (-> @entity-atom field str/blank? not))

;; [TODO] Needs cleanup
(defn add-field [into-atom & {:keys [label field type] :or {type "text"}}]
  [na/form-input {:inline? true
                 :label label
                 :placeholder (-> field name (str "..."))
                 :type (or type "text")
                 :default-value (or (field @into-atom) "")
                 :on-change #(swap! into-atom assoc field (.-value %2))}])

(defn add-user []
  (let [new-user (reagent/atom {})]
    (fn []
      (let [i-am-admin? (:user/isAdmin (<sub [:user]))
            editor? (or (-> @new-user :permissions :user/isAdmin)
                        (-> @new-user :permissions :user/isEditor))
            consumer? (-> @new-user :permissions :user/isConsumer)]
        [na/form-group {}
         (add-field new-user :label "Name" :field :name)
         (when editor?
           (add-field new-user :label "Password" :field :password :type "password"))
         (when consumer?
           (add-field new-user :label "Abbrev" :field :abbrev))
         (when editor?
           (add-field new-user :label "Email" :field :email :type "Email"))
         [nax/labelled-field
          :label "Permissions:"
          :inline? true
          :content [na/dropdown {:multiple? true
                                 :value (na/<atom new-user #{} :permissions)
                                 :on-change ;; [TODO] Can this be generalized?
                                            #(->> (.-value %2)
                                                 (map (fn [x] (keyword "user" x)))
                                                 (into #{})
                                                 (swap! new-user assoc :permissions))
                                 :options (if i-am-admin?
                                            [(na/list-option :user/isAdmin "Administrator")
                                             (na/list-option :user/isEditor "Editor")
                                             (na/list-option :user/isConsumer "Consumer")]
                                            [(na/list-option :user/isConsumer "Consumer")])}]]
         [na/form-button {:on-click #(do (>evt [:add-user @new-user])
                                         (reset! new-user {}))
                          :content "Add User"
                          :data-tooltip "Create a new user"
                          :positive? true
                          :disabled? (not (and (has? new-user :name)
                                               (or (and consumer? (has? new-user :abbrev))
                                                   (and editor? (has? new-user :password) (has? new-user :email)))))}]]))))

(defn add-source []
  (let [new-source (reagent/atom {})]
    (fn []
      [na/form-group {}
       (add-field new-source :label "Name" :field :name)
       (add-field new-source :label "Abbrev" :field :abbrev)
       [na/form-button {
                        :on-click #(do (>evt [:add-source @new-source])
                                       (reset! new-source {}))
                        :content "Add Source"
                        :data-tooltip "Create a new source"
                        :positive? true
                        :disabled? (not (and (has? new-source :name)
                                             (has? new-source :abbrev)))}]])))

(defn add-category  []
  (let [new-category (reagent/atom {})]
    (fn []
      [na/form-group {}
       (add-field new-category :label "Name" :field :name)
       (add-field new-category :label "Description" :field :description)
       [na/form-button {:on-click #(do (>evt [:add-category @new-category])
                                       (reset! new-category {}))
                        :content "Add Category"
                        :data-tooltip "Create a new category"
                        :positive? true
                        :disabled? (not (and (has? new-category :name)
                                             (has? new-category :description)))}]])))

(defn add-currency  []
  (let [new-currency (reagent/atom {})]
    (fn []
      [na/form-group {}
       (add-field new-currency :label "Name" :field :name)
       (add-field new-currency :label "Abbrev" :field :abbrev)
       [na/form-button {:on-click #(do (>evt [:add-currency @new-currency])
                                       (reset! new-currency {}))
                        :content "Add Currency"
                        :data-tooltip "Create a new currency"
                        :positive? true
                        :disabled? (not (and (has? new-currency :name)
                                             (has? new-currency :abbrev)))}]])))

(defn add-vendor []
  (let [categories (<sub [:categories])
        category (reagent/atom (-> categories first :db/id))
        new-vendor (reagent/atom "")]
    (fn []
      [na/form-group {}
       [na/form-input {:inline? true :label "Category"}
        [na/dropdown {:default-value (or @category "")
                      :options (na/dropdown-list categories :db/id :category/name)
                      :on-change #(reset! category (.-value %2))}]]
       ;; [TODO] Merge this code with add-field, above
       [na/form-input {:inline? true
                       :label "Vendor"
                       :placeholder "Vendor..."
                       :type "text"
                       :default-value @new-vendor
                       :on-change (na/>atom new-vendor)}]
       [na/form-button {:on-click #(do (>evt [:add-vendor @category @new-vendor])
                                       (reset! new-vendor ""))
                        :content "Add Vendor"
                        :data-tooltip "Create a new vendor"
                        :positive? true
                        :disabled? (empty? @new-vendor)}]])))

(defn edit-panel []
  (let [actions [(na/list-option :add "Add")
                 (na/list-option :edit "Edit")]
        entity-names [(na/list-option :user "User")
                      (na/list-option :source "Source")
                      (na/list-option :category "Category")
                      (na/list-option :currency "Currency")
                      (na/list-option :vendor "vendor")]
        action (reagent/atom :add)
        entity (reagent/atom :vendor)]
    (fn []
      (if (<sub [:user])
        [na/form {}
         [nax/panel-header "Schema Editor"]
         [na/form-input {:inline? true :label "Action"}
          [na/dropdown {:default-value @action
                        :options actions
                        :on-change (na/>atom action :add keyword)}]
          [na/dropdown {:default-value @entity
                        :options entity-names
                        :on-change (na/>atom entity :vendor keyword)}]]
         [nax/panel-subheader (str (utils/get-at actions :value @action :text) " "
                                   (utils/get-at entity-names :value @entity :text))]
         (case @action
           :add (case @entity
                  :user     [add-user]
                  :source   [add-source]
                  :category [add-category]
                  :currency [add-currency]
                  :vendor   [add-vendor]
                  [:div "ERROR??"])
           :edit [:div "NYI"])]
        [login-panel]))))

(defn about-panel []
  [:div
   (nax/panel-header "About")
   [:p "Third iteration of a simple receipts management program."]
   [:p "This time, my focus is on learning Vase, Datomic, and Pedestal."]
   [:h4 "Status"]
   [:p (goog.string/format "Currently holding %d purchases." (or (:purchases-count (<sub [:about-server])) 0))]
   [:h4 (goog.string/format "Version %s" (:version (<sub [:about-server])))]
   [:h5 "Includes libraries:"]
   [:small
    [:ul
     (map (fn [[dependency version]]
            ^{:key dependency}[:li (goog.string/format "%s:%s" dependency version)])
          (sort-by first (:dependencies (<sub [:about-server]))))]]
   [:p
    [:em "Copyright (c) 2017, David Goldfarb <deg@degel.com>"]
    [:br]
    [:em "Portions copyright 2013-2016."]]])

(def date-format (time-format/formatter "ddMMMyy"))
(def time-format (time-format/formatter "HH:mm:ss"))

(defn field-output [field value]
  (case field
    :consumer (str/join ", " value)
    :date (time-format/unparse date-format (time-coerce/to-date-time value))
    :time (time-format/unparse time-format (time-coerce/to-date-time value))
    :price (let [[currency amount] value
                 symbol (currency-symbol currency)]
             (str symbol amount))
    (str value)))

(defn history-cell [id-fn format value]
  ^{:key (-> format name id-fn)}[:td (field-output format value)])

(defn history-table [purchases]
  [:table.table.table-striped.table-bordered.table-condensed
   [:thead [:tr
            (map (fn [h] ^{:key h}[:td h])
                 ["Source" "Date" "Time" "Price" "Category" "Vendor" "Comment" "Consumer" "User"])]]
   [:tbody (map (fn [{:purchase/keys [source date currency price category vendor comment consumer user] :as purchase}]
                  (let [row-id (:db/id purchase)
                        id-fn (partial str row-id "-")]
                    ^{:key row-id} [:tr
                                    (history-cell id-fn :source source)
                                    (history-cell id-fn :date date)
                                    (history-cell id-fn :time date)
                                    (history-cell id-fn :price [currency price])
                                    (history-cell id-fn :category category)
                                    (history-cell id-fn :vendor vendor)
                                    (history-cell id-fn :comment comment)
                                    (history-cell id-fn :consumer consumer)
                                    (history-cell id-fn :user user)]))
                purchases)]])

(defn history-csv [csv]
  [na/text-area {:rows  12
                 :disabled? true
                 :style {:width "100%"
                         :font-family "Monospace"}
                 :value (or csv "")}])

(defn history-panel []
  (if (<sub [:user])
    (let [consumer? (:user/isConsumer (<sub [:user]))
          editor? (:user/isEditor (<sub [:user]))
          admin? (:user/isAdmin (<sub [:user]))]
      [:div
       (nax/panel-header "History")
       [history-table (<sub [:history])]
       [na/form-button {:on-click (na/>event [:get-history])
                        :content "Refresh History"
                        :data-tooltip "Load history from server"
                        :positive? true}]
       (when admin?
         [history-csv (<sub [:history-csv])])])
    [login-panel]))

(defn formatted-schema [title schema-part only-dynamic?]
  [:div
   [nax/subsection-header title]
   [:ul (map (fn [tuple]
               ^{:key (:db/id tuple)}
               [:li {:style {:list-style "none"}}
                (str (dissoc tuple :db/id :receipts/dynamic?))])
             (if only-dynamic?
               (filter :receipts/dynamic? schema-part)
               schema-part))]])

(defn config-panel []
  (let [verbose? (reagent/atom false)
        entities (reagent/atom "")]
    (fn []
      (let [email (:user/email (<sub [:user]))
            consumer? (:user/isConsumer (<sub [:user]))
            editor? (:user/isEditor (<sub [:user]))
            admin? (:user/isAdmin (<sub [:user]))
            schema (<sub [:schema])]
        [na/form {:widths "equal"}
         (nax/panel-header "Setup")
         [na/grid {}
          [:div (str "Logged in as " (or email "[UNKNOWN USER]"))]
          [na/form-button {:on-click (na/>event [:logout])
                           :content "Logout"
                           :data-tooltip "Logout from server"
                           :negative? true
                           :size "tiny"}]]
         (nax/section-header "Developer tools")

         (when-not admin?
           [unavailable])
         (when admin?
           [na/form-group {}
            [na/form-input {:inline? true :label "Choose server"}
             [na/dropdown {:default-value (<sub [:server])
                           :options [(na/list-option :production "production")
                                     (na/list-option :development "development")]
                           :on-change (na/>event [:set-server] :development keyword)}]]])
         (when admin?
           [na/text-area {:placeholder "Entities..."
                          :rows 5
                          :default-value @entities
                          :on-change (na/>atom entities)}])
         (when admin?
           [na/form-button {:on-click (na/>event [:load-entities (reader/read-string (str "[" @entities "]"))])
                            :content "Load entities"
                            :data-tooltip "Add objects, e.g. saved from a previous database instantiation"
                            :positive? true}])
         (nax/section-header "Schema")
         [na/checkbox {:label "Show all?"
                       :checked? @verbose?
                       :on-change (na/>atom verbose?)}]
         (formatted-schema "Users" (:users schema) (not @verbose?))
         (formatted-schema "Sources" (:sources schema) (not @verbose?))
         (formatted-schema "Currencies" (:currencies schema) (not @verbose?))
         (formatted-schema "Categories" (:categories schema) (not @verbose?))
         (formatted-schema "Vendors" (:vendors schema) (not @verbose?))]))))

(defn setup-panel []
  (if (<sub [:credentials])
    [config-panel]
    [login-panel]))


;; [TODO] Delete if not needed
(defn wrap-page [page]
  [na/container {} page])

(def tabs [{:id :home    :label "receipt"   :panel (wrap-page [home-panel])}
           {:id :edit    :label "edit"      :panel (wrap-page [edit-panel])}
           {:id :history :label "history"   :panel (wrap-page [history-panel])}
           {:id :about   :label "about"     :panel (wrap-page [about-panel])}
           {:id :setup   :label "setup"     :panel (wrap-page [setup-panel])}])

(defn tab-panel []
  (:panel (or (first (filter #(= (:id %) (<sub [:page]))
                             tabs))
              :home-panel)))

(defn tabs-row []
  (into [na/menu {:tabular? true}]
        (map (fn [{:keys [id label]}]
               (let [active? (= id (or (<sub [:page]) :home))
                     handler #(routes/goto-page id (<sub [:server]))]
                 [na/menu-item {:name label
                                :active? active?
                                :color (if active? "blue" "grey")
                                :on-click handler}]))
             tabs)))

(defn main-panel []
  (let [schema (re-frame/subscribe [:schema])]
    (fn []
      ;; [TODO] This gets the schema once when the program starts. This is probably wrong:
      ;; - I don't know if this is called at the best time or place
      ;; - No mechanism to get changed schema; especially, e.g., if a client is open for many days
      (>evt [:update-credentials])
      [:div
       [nax/app-header [:name]]
       [tabs-row]
       [tab-panel]])))
