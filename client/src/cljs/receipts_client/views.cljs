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
   [re-com.core :as re-com]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [receipts-client.routes :as routes]
   [receipts-client.utils :as utils]
   [receipts.specs :as specs]
   [struct.core :as struct]))


;; home

(def tight-gap "0.2rem")
(def std-gap "0.5rem")
(def title-gap "0.5rem")
(def title-width "7em")
(def tiny-field-width "5rem")
(def tight-field-width "11rem")
(def field-width "14rem")


;; Nice sugar from https://lambdaisland.com/blog/11-02-2017-re-frame-form-1-subscriptions
(def <sub (comp deref re-frame/subscribe))
(def >evt re-frame/dispatch)

(defn app-title []
  [re-com/title
   :label (str (<sub [:name])
               (when (= (<sub [:server]) :development)
                 " (local test server)"))
   :level :level1])

(defn panel-title [label]
  [re-com/title :label label :level :level1])

(defn panel-subtitle [label]
  [re-com/title :label label :level :level2])

(defn section-title [label]
  [re-com/title :label label :level :level3])

(defn subsection-title [label]
  [re-com/title :label label :level :level4])

(defn button [dispatch label tooltip]
  {:pre [(specs/validate (s/or :dispatch ::specs/event-vector :fn ifn?) dispatch)
         (specs/validate string? label)
         (specs/validate string? tooltip)]}
  [re-com/button
   :label label
   :tooltip tooltip
   :on-click #(if (vector? dispatch)
                (>evt dispatch)
                (dispatch))])

(defn labelled [label error component]
  {:pre [(specs/validate string? label)
         (specs/validate (s/nilable string?) error)]}
  (fn [label error component]
    [re-com/h-box
     :width "100%"
     :gap std-gap
     :children [[re-com/label
                 :class (if error "errmsg" "")
                 :width title-width
                 :label (str label (if (str/blank? label) "" ": "))]
                [re-com/v-box :children [component
                                         (when error
                                           [re-com/label :class "errmsg" :label error])]]]]))

;;; Per re-com, new-date-time is a goog.date.Date, while new-time is an integer encoding
;;; (+ minutes (* 100 hours)).
;;; old-date-time, and the retval, on the other hand, are instances of goog.date.DateTime
(defn update-date-time [old-date-time new-date-time new-time]
  {:pre [(specs/validate (s/or :goog utils/goog-date? :inst inst?) old-date-time)
         (specs/validate (s/or :goog utils/goog-date? :inst inst?) new-date-time)
         (specs/validate (s/nilable int?) new-time)]
   :post [(specs/validate inst? %)]}
  (let [old-date-time (time-coerce/to-date-time old-date-time)]
    (time-coerce/to-date
     (time/date-time (time/year  (or new-date-time old-date-time))
                     (time/month (or new-date-time old-date-time))
                     (time/day   (or new-date-time old-date-time))
                     (if new-time (quot new-time 100) (time/hour old-date-time))
                     (if new-time (mod new-time 100) (time/minute old-date-time))
                     (time/second old-date-time)))))

(defn date-time-picker [& {:keys [model on-change show-multi-year? show-time?]}]
  (let [date (time-coerce/to-date-time model)
        time (if date (+ (* 100 (time/hour date)) (time/minute date)) 0)
        date-atom (reagent/atom date)
        time-atom (reagent/atom time)]
    [re-com/h-box
     :gap tight-gap
     :align :center
     :children [(when show-multi-year?
                  [re-com/button
                   :label "<-5 yrs"
                   :class "btn-primary btn-sm"
                   :on-click #(do (swap! date-atom time/minus (time/years 5))
                                  (on-change (update-date-time model @date-atom nil)))])
                [re-com/datepicker-dropdown
                 :show-today? true
                 :model date-atom
                 :on-change #(do (reset! date-atom %)
                                 (on-change (update-date-time model % nil)))]
                (when show-multi-year?
                  [re-com/button
                   :label "5 yrs->"
                   :class "btn-primary btn-sm"
                   :on-click #(do (swap! date-atom time/plus (time/years 5))
                                  (on-change (update-date-time model @date-atom nil)))])
                (when show-time?
                  [re-com/input-time
                   :model time-atom
                   :show-icon? true
                   :on-change #(do (reset! time-atom %)
                                   (on-change (update-date-time model nil %)))])]]))

(defn dropdown
  "Dropdown component

  multiple? - Allow multiple selection
  "
  [& {:keys [multiple? field-key subs-key filter-fn schema-key schema-id-key schema-label-key]
      :or {model-key-fn identity
           schema-id-key schema-key
           schema-label-key schema-key}}]
  [(if multiple? re-com/selection-list re-com/single-dropdown)
   :width field-width
   :model ((if multiple? (partial into #{}) identity)
           (field-key (<sub [:current-receipt])))
   :choices (mapv (fn [{id schema-id-key
                        label schema-label-key}]
                    {:id id :label label})
                  (if filter-fn
                    (filter filter-fn (<sub [subs-key]))
                    (<sub [subs-key])))
   :on-change #(>evt [:edit-current-receipt field-key %])])


(defn login-panel []
  (let [email (reagent/atom "")
        password (reagent/atom "")]
    (fn []
      [re-com/v-box
       :gap title-gap
       :children [(panel-title "Login")
                  [labelled "Email" nil
                   [re-com/input-text
                    :width field-width
                    :model email
                    :on-change #(reset! email %)
                    :attr {:type "Email"}]]
                  [labelled "Password" nil
                   [re-com/input-text
                    :width field-width
                    :model password
                    :on-change #(reset! password %)
                    :attr {:type "Password"}]]
                  [button [:login @email @password] "Login" "Login to server"]]])))

(defn unavailable []
  [:div [:em "(Unavailable)"]])

;;; See https://funcool.github.io/struct/latest/
;;; [TODO] Replace with spec, asap.
(def complete-receipt
  {:purchase/source [[struct/required :message "'Source' missing"] struct/string]
   :purchase/date [[struct/required :message "Please specify date"] struct/positive]
   :purchase/price [[struct/required :message "Please specify amount"] struct/positive]
   :purchase/category [[struct/required :message  "Category missing"] struct/string]
   :purchase/vendor [[struct/required :message  "Choose vendor in category"] struct/string]
   :purchase/consumer [[struct/required :message  "Specify user(s) of this purchase"] struct/set]})

(defn validate-receipt [receipt]
  (struct/validate receipt complete-receipt))

(defn valid-receipt? [receipt]
  (struct/valid? receipt complete-receipt))

(defn receipt-page []
  (let [receipt (<sub [:current-receipt])
        validation-errors (first (validate-receipt receipt))]
    [re-com/v-box
     :gap tight-gap
     :children
     [[labelled "Source"
       (:purchase/source validation-errors)
       [dropdown :multiple? false
        :field-key :purchase/source
        :subs-key :sources
        :schema-id-key :source/abbrev
        :schema-label-key :source/name]]
      [labelled "Date"
       (:purchase/date validation-errors)
       [date-time-picker
        :model (or (:purchase/date receipt) (time-coerce/to-date (time/now)))
        :on-change #(>evt [:edit-current-receipt :purchase/date %])]]
      [labelled "Price"
       (:purchase/price validation-errors)
       [re-com/input-text
        :width field-width
        :model (or (:purchase/price receipt) "0.00")
        :on-change #(>evt [:edit-current-receipt :purchase/price %])
        :change-on-blur? false
        :attr {:type "number"
               :step "0.01"}]]
      [labelled "Category"
       (:purchase/category validation-errors)
       [dropdown :multiple? false
        :field-key :purchase/category
        :subs-key :categories
        :schema-key :category/name]]
      [labelled "Vendor"
       (:purchase/vendor validation-errors)
       [dropdown :multiple? false
        :field-key :purchase/vendor
        :subs-key :vendors
        :schema-key :vendor/name
        :filter-fn #(some #{(:purchase/category receipt)} (:vendor/category %))]]
      [labelled "Comment"
       (:purchase/comment validation-errors)
       [re-com/input-text
        :width field-width
        :model (or (:purchase/comment receipt) "")
        :on-change #(>evt [:edit-current-receipt :purchase/comment %])
        :attr {:type "text"}]]
      [labelled "Consumer"
       (:purchase/consumer validation-errors)
       [dropdown :multiple? true
        :field-key :purchase/consumer
        :subs-key :users
        :filter-fn :user/isConsumer
        :schema-label-key :user/name
        :schema-id-key :user/abbrev]]
      [re-com/gap :size std-gap]
      [re-com/h-box
       :children
       [[re-com/button
         :disabled? (not (valid-receipt? receipt))
         :on-click #(>evt [:submit-receipt (-> receipt
                                               (update :purchase/date time-coerce/to-date)
                                               (update :purchase/price js/parseFloat))])
         :label "Submit Receipt"]]]]]))

(defn interstitial-page []
  [re-com/v-box
   :gap tight-gap
   :children [[re-com/h-box
               :children [[:img {:src (str "http://thecatapi.com/api/images/get?format=src&size=small&"
                                           "cacheBuster=" (str (rand)))}]
                          [re-com/gap :size "1"]]]
              [button [:next-receipt] "Continue" "enter next receipt"]]])

(defn home-panel []
  (if (:user/isEditor (<sub [:user]))
    [re-com/v-box
     :gap title-gap
     :children (if (<sub [:receipt-stored?])
                 [(panel-title "Receipt entered") [interstitial-page]]
                 [(panel-title "New Receipt")     [receipt-page]])]
    [login-panel]))

(defn add-user []
  (let [new-user (reagent/atom {})]
    (fn []
      (let [editor? (or (-> @new-user :permissions :user/isAdmin)
                        (-> @new-user :permissions :user/isEditor))
            consumer? (-> @new-user :permissions :user/isConsumer)]
        [re-com/v-box
         :gap std-gap
         :children [[labelled "Name" nil
                     [re-com/input-text
                      :width field-width
                      :model (or (:name @new-user) "")
                      :on-change #(swap! new-user assoc :name %)
                      :attr {:type "text"}]]
                    (when editor?
                      [labelled "Password" nil
                       [re-com/input-text
                        :width field-width
                        :model (or (:password @new-user) "")
                        :on-change #(swap! new-user assoc :password %)
                        :attr {:type "password"}]])
                    (when consumer?
                      [labelled "Abbrev" nil
                       [re-com/input-text
                        :width field-width
                        :model (or (:abbrev @new-user) "")
                        :on-change #(swap! new-user assoc :abbrev %)
                        :attr {:type "text"}]])
                    (when editor?
                      [labelled "Email" nil
                       [re-com/input-text
                        :width field-width
                        :model (or (:email @new-user) "")
                        :on-change #(swap! new-user assoc :email %)
                        :attr {:type "Email"}]])
                    [labelled "Permissions" nil
                     [re-com/selection-list
                      :choices [{:id :user/isAdmin :label "Administrator"}
                                {:id :user/isEditor :label "Editor"}
                                {:id :user/isConsumer :label "Consumer"}]
                      :model (or (:permissions @new-user) #{})
                      :on-change #(swap! new-user assoc :permissions %)]]
                    [button #(do (>evt [:add-user @new-user])
                                 (reset! new-user {}))
                     "Add User" "Create a new user"]]]))))

(defn add-source []
  [:div "NYI (sources)"])

(defn add-category  []
  [:div "NYI (categories)"])

(defn add-vendor []
  (let [categories (re-frame/subscribe [:categories])
        category (reagent/atom (-> @categories first :db/id))
        vendor (reagent/atom "")]
    (fn []
      [re-com/v-box
       :gap std-gap
       :children [[labelled "Category" nil
                   [re-com/single-dropdown
                    :width field-width
                    :choices categories
                    :id-fn :db/id
                    :label-fn :category/name
                    :model category
                    :on-change #(reset! category %)]]
                  [labelled "Vendor" nil
                   [re-com/input-text
                    :model vendor
                    :on-change #(reset! vendor %)
                    :width field-width]]
                  [button #(do (>evt [:add-vendor @category @vendor])
                               (reset! vendor ""))
                   "Add Vendor" "Create a new vendor"]]])))

(def entity-names [{:id :user :label "User"}
                   {:id :source :label "Source"}
                   {:id :category :label "Category"}
                   {:id :vendor :label "vendor"}])

(defn edit-panel []
  (let [user (re-frame/subscribe [:user])
        actions [{:id :add :label "Add"}
                 {:id :edit :label "Edit"}]
        action (reagent/atom :add)
        entity (reagent/atom :vendor)]
    (fn []
      (if @user
        (let [admin? (:user/isAdmin @user)]
          [re-com/v-box
           :gap title-gap
           :children [[panel-title "Schema Editor"]
                      [labelled "Action" nil
                       [re-com/h-box
                        :gap std-gap
                        :children [[re-com/single-dropdown :width tiny-field-width
                                    :choices actions
                                    :model action
                                    :on-change #(reset! action %)]
                                   [re-com/single-dropdown :width tight-field-width
                                    :choices entity-names
                                    :model entity
                                    :on-change #(reset! entity %)]]]]
                      [panel-subtitle (str (utils/get-at actions :id @action :label) " "
                                           (utils/get-at entity-names :id @entity :label))]
                      (case @action
                        :add (case @entity
                               :user (if admin? [add-user] [unavailable])
                               :source [add-source]
                               :category [add-category]
                               :vendor [add-vendor]
                               [:div "ERROR??"])
                        :edit [:div "NYI"])]])
        [login-panel]))))

(defn about-panel []
  [re-com/v-box
   :gap title-gap
   :children [(panel-title "About")
              [:div
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
                [:em "Portions copyright 2013-2016."]]]]])

(def date-format (time-format/formatter "ddMMMyy"))
(def time-format (time-format/formatter "HH:mm:ss"))

(defn field-output [field value]
  (case field
    :consumer (str/join ", " value)
    :date (time-format/unparse date-format (time-coerce/to-date-time value))
    :time (time-format/unparse time-format (time-coerce/to-date-time value))
    :price (let [[currency amount] value
                 symbol (case currency
                          "USD" "$"
                          "EU"  "\u20AC"
                          "GBP" "\u00A3"
                          "NIS" "\u20AA"
                          "?")]
             (str symbol amount))
    (str value)))

(defn history-cell [id-fn format value]
  ^{:key (-> format name id-fn)}[:td (field-output format value)])

(defn history-table [purchases]
  [:table.table.table-striped.table-bordered.table-condensed
   [:thead [:tr
            (map (fn [h] ^{:key h}[:td h])
                 ["Source" "Date" "Time" "Price" "Category" "Vendor" "Comment" "Consumer"])]]
   [:tbody (map (fn [{:purchase/keys [source date currency price category vendor comment consumer] :as purchase}]
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
                                    (history-cell id-fn :consumer consumer)]))
                purchases)]])

(defn history-csv [csv]
  [re-com/input-textarea
   :model (or csv "")
   :on-change #()
   :rows 20
   :width "100%"
   :style {:font-family "Monospace"}
   :disabled? true])

(defn history-panel []
  (if (<sub [:user])
    (let [consumer? (:user/isConsumer (<sub [:user]))
          editor? (:user/isEditor (<sub [:user]))
          admin? (:user/isAdmin (<sub [:user]))]
      [re-com/v-box
       :gap title-gap
       :children [(panel-title "History")
                  [re-com/scroller
                   :scroll :auto
                   :child [history-table (<sub [:history])]]
                  (when admin?
                    [history-csv (<sub [:history-csv])])
                  [re-com/h-box
                   :children [(button [:get-history] "Refresh History" "Load history from server")]]]])
    [login-panel]))

(defn formatted-schema [title schema-part only-dynamic?]
  [re-com/v-box
   :children [[subsection-title title]
              [:ul (map (fn [tuple]
                        ^{:key (:db/id tuple)}
                          [:li {:style {:list-style "none"}}
                           (str (dissoc tuple :db/id :receipts/dynamic?))])
                        (if only-dynamic?
                          (filter :receipts/dynamic? schema-part)
                          schema-part))]]])

(defn config-panel []
  (let [verbose? (reagent/atom false)
        entities (reagent/atom "")]
    (fn []
      (let [email (:user/email (<sub [:user]))
            consumer? (:user/isConsumer (<sub [:user]))
            editor? (:user/isEditor (<sub [:user]))
            admin? (:user/isAdmin (<sub [:user]))]
        [re-com/v-box
         :gap title-gap
         :children [(panel-title "Setup")
                    [re-com/h-box
                     :gap std-gap
                     :children [[:span (str "Logged in as " (or email "[UNKNOWN USER]"))]
                                [button [:logout] "Logout" "Logout from server"]]]
                    (section-title "Developer tools")
                    (when-not admin?
                      [unavailable])
                    (when admin?
                      [labelled "Choose server" nil
                       [re-com/single-dropdown
                        :choices [{:id :production :label "production"}
                                  {:id :development :label "development"}]
                        :width tight-field-width
                        :model (<sub [:server])
                        :on-change #(>evt [:set-server %])]])
                    (when admin?
                      [re-com/v-box
                       :align :end
                       :gap std-gap
                       :children [[re-com/input-textarea
                                   :model entities
                                   :on-change #(reset! entities %)
                                   :width "100%"
                                   :rows 5]
                                  (button [:load-entities (reader/read-string (str "[" @entities "]"))]
                                          "Load entities"
                                          "Add objects, e.g. saved from a previous database instantiation")]])
                    (section-title "Schema")
                    (re-com/checkbox
                     :model verbose?
                     :on-change #(reset! verbose? %)
                     :label "Show all?")
                    (when admin?
                      (formatted-schema "Users" (:users (<sub [:schema])) (not @verbose?)))
                    (formatted-schema "Sources" (:sources (<sub [:schema])) (not @verbose?))
                    (formatted-schema "Currencies" (:currencies (<sub [:schema])) (not @verbose?))
                    (formatted-schema "Categories" (:categories (<sub [:schema])) (not @verbose?))
                    (formatted-schema "Vendors" (:vendors (<sub [:schema])) (not @verbose?))]]))))

(defn setup-panel []
  (if (<sub [:credentials])
    [config-panel]
    [login-panel]))


(defn wrap-page [page]
  [re-com/border
   :width "98vw"
   :margin "1vmin"
   :border "0.2vmin solid lightgrey"
   :padding "2vmin"
   :child [re-com/scroller :scroll :auto :child page]])

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
  [re-com/horizontal-pill-tabs
   :tabs tabs
   :model (or (<sub [:page]) :home)
   :on-change #(routes/goto-page % (<sub [:server]))])

(defn main-panel []
  (let [schema (re-frame/subscribe [:schema])]
    (fn []
      ;; [TODO] This gets the schema once when the program starts. This is probably wrong:
      ;; - I don't know if this is called at the best time or place
      ;; - No mechanism to get changed schema; especially, e.g., if a client is open for many days
      (>evt [:update-credentials])
      [re-com/v-box
       :children [[app-title]
                  [tabs-row]
                  [tab-panel]]])))
