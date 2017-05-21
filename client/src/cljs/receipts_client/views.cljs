;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.views
  (:require
   [cljs-time.coerce :as time-coerce]
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [clojure.string :as str]
   [re-com.core :as re-com]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [receipts-client.routes :as routes]
   [receipts-client.utils :as utils]
   [struct.core :as struct]))


;; home

(defn app-title []
  (let [name (re-frame/subscribe [:name])
        server (re-frame/subscribe [:server])]
    (fn []
      [re-com/title
       :label (str @name (when (= @server :development) " (local test server)"))
       :level :level1])))

(defn panel-title [label]
  [re-com/title :label label :level :level1])

(defn panel-subtitle [label]
  [re-com/title :label label :level :level2])

(defn section-title [label]
  [re-com/title :label label :level :level3])

(defn subsection-title [label]
  [re-com/title :label label :level :level4])

(defn button [dispatch label tooltip]
  [re-com/button
   :label label
   :tooltip tooltip
   :on-click #(if (vector? dispatch)
                (re-frame/dispatch dispatch)
                (dispatch))])

(defn labelled [label error component]
  (fn [label error component]
    [re-com/h-box
     :width "100%"
     :gap "1rem"
     :children [[re-com/label
                 :class (if error "errmsg" "")
                 :width "8rem"
                 :label (str label (if (str/blank? label) "" ": "))]
                [re-com/v-box :children [component
                                         (when error
                                           [re-com/label :class "errmsg" :label error])]]]]))

;;; Per re-com, new-date-time is a goog.date.Date, while new-time is an integer encoding
;;; (+ minutes (* 100 hours)).
;;; old-date-time, and the retval, on the other hand, are instances of goog.date.DateTime
;;; [TOOD] This would be a great first place to learn spec pre-condition syntax
(defn update-date-time [old-date-time new-date-time new-time]
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
     :gap "0.2rem"
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
  (let [current-receipt (re-frame/subscribe [:current-receipt])
        schema (re-frame/subscribe [subs-key])]
    [(if multiple? re-com/selection-list re-com/single-dropdown)
     :width "15rem"
     :model ((if multiple? (partial into #{}) identity)
             (field-key @current-receipt))
     :choices (sort-by :label (mapv (fn [{id schema-id-key
                                          label schema-label-key}]
                                      {:id id :label label})
                                    (if filter-fn
                                      (filter filter-fn @schema)
                                      @schema)))
     :on-change #(re-frame/dispatch [:edit-current-receipt field-key %])]))


(defn login-panel []
  (let [email (reagent/atom "")
        password (reagent/atom "")]
    (fn []
      [re-com/v-box
       :gap "0.5rem"
       :children [(panel-title "Login")
                  [labelled "Email" nil
                   [re-com/input-text
                    :width "15rem"
                    :model email
                    :on-change #(reset! email %)
                    :attr {:type "Email"}]]
                  [labelled "Password" nil
                   [re-com/input-text
                    :width "15rem"
                    :model password
                    :on-change #(reset! password %)
                    :attr {:type "Password"}]]
                  [button [:login @email @password] "Login" "Login to server"]]])))

(defn unavailable []
  [:div [:em "(Unavailable)"]])

;;; See https://funcool.github.io/struct/latest/
;;; [TODO] Replace with spec, asap.
(def complete-receipt
  {:purchase/paymentMethod [[struct/required :message "'Paid by' missing"] struct/string]
   :purchase/date [[struct/required :message "Please specify date"] struct/positive]
   :purchase/category [[struct/required :message  "Category missing"] struct/string]
   :purchase/vendor [[struct/required :message  "Choose vendor in category"] struct/string]
   :purchase/forWhom [[struct/required :message  "Specify user(s) of this purchase"] struct/set]})

(defn validate-receipt [receipt]
  (struct/validate receipt complete-receipt))

(defn valid-receipt? [receipt]
  (struct/valid? receipt complete-receipt))

(defn receipt-page []
  (let [current-receipt (re-frame/subscribe [:current-receipt])]
    (fn []
      (let [validation-errors (first (validate-receipt @current-receipt))]
        [re-com/v-box
         :gap "0.2rem"
         :children
         [[labelled "Paid by"
           (:purchase/paymentMethod validation-errors)
           [dropdown :multiple? false
            :field-key :purchase/paymentMethod
            :subs-key :payment-methods
            :schema-key :paymentMethod/name]]
          [labelled "Date"
           (:purchase/date validation-errors)
           [date-time-picker
            :model (or (:purchase/date @current-receipt) (time/now))
            :on-change #(re-frame/dispatch [:edit-current-receipt :purchase/date %])]]
          [labelled "Price"
           (:purchase/price validation-errors)
           [re-com/input-text
            :width "15rem"
            :model (or (:purchase/price @current-receipt) "0.00")
            :on-change #(re-frame/dispatch [:edit-current-receipt :purchase/price %])
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
            :filter-fn #(some #{(:purchase/category @current-receipt)} (:vendor/category %))]]
          [labelled "Comment"
           (:purchase/comment validation-errors)
           [re-com/input-text
            :width "15rem"
            :model (or (:purchase/comment @current-receipt) "")
            :on-change #(re-frame/dispatch [:edit-current-receipt :purchase/comment %])
            :attr {:type "text"}]]
          [labelled "For Whom"
           (:purchase/forWhom validation-errors)
           [dropdown :multiple? true
            :field-key :purchase/forWhom
            :subs-key :users
            :filter-fn :user/isConsumer
            :schema-label-key :user/name
            :schema-id-key :user/abbrev]]
          [re-com/gap :size "0.5rem"]
          [re-com/h-box
           :children
           [[re-com/button
             :disabled? (not (valid-receipt? @current-receipt))
             :on-click #(re-frame/dispatch [:submit-receipt @current-receipt])
             :label "Submit Receipt"]]]]]))))

(defn home-panel []
  (let [user (re-frame/subscribe [:user])]
    (fn []
      (if (:user/isEditor @user)
        [re-com/v-box
         :gap "1rem"
         :children [(panel-title "New Receipt")
                    [receipt-page]]]
        [login-panel]))))

(defn add-user []
  (let [new-user (reagent/atom {})]
    (fn []
      (let [editor? (or (-> @new-user :permissions :user/isAdmin)
                        (-> @new-user :permissions :user/isEditor))
            consumer? (-> @new-user :permissions :user/isConsumer)]
        [re-com/v-box
         :gap "0.5rem"
         :children [[labelled "Name" nil
                     [re-com/input-text
                      :width "15rem"
                      :model (or (:name @new-user) "")
                      :on-change #(swap! new-user assoc :name %)
                      :attr {:type "text"}]]
                    (when editor?
                      [labelled "Password" nil
                       [re-com/input-text
                        :width "15rem"
                        :model (or (:password @new-user) "")
                        :on-change #(swap! new-user assoc :password %)
                        :attr {:type "password"}]])
                    (when consumer?
                      [labelled "Abbrev" nil
                       [re-com/input-text
                        :width "15rem"
                        :model (or (:abbrev @new-user) "")
                        :on-change #(swap! new-user assoc :abbrev %)
                        :attr {:type "text"}]])
                    (when editor?
                      [labelled "Email" nil
                       [re-com/input-text
                        :width "15rem"
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
                    [button #(do (re-frame/dispatch [:add-user @new-user])
                                 (reset! new-user {}))
                     "Add User" "Create a new user"]]]))))

(defn add-payment-method []
  [:div "NYI (payment methods)"])

(defn add-category  []
  [:div "NYI (categories)"])

(defn add-vendor []
  (let [categories (re-frame/subscribe [:categories])
        category (reagent/atom (-> @categories first :db/id))
        vendor (reagent/atom "")]
    (fn []
      [re-com/v-box
       :gap "0.5rem"
       :children [[labelled "Category" nil
                   [re-com/single-dropdown
                    :width "12rem"
                    :choices categories
                    :id-fn :db/id
                    :label-fn :category/name
                    :model category
                    :on-change #(reset! category %)]]
                  [labelled "Vendor" nil
                   [re-com/input-text
                    :model vendor
                    :on-change #(reset! vendor %)
                    :width "12rem"]]
                  [button #(do (re-frame/dispatch [:add-vendor @category @vendor])
                               (reset! vendor ""))
                   "Add Vendor" "Create a new vendor"]]])))

(defn edit-panel []
  (let [user (re-frame/subscribe [:user])
        actions [{:id :add :label "Add"}
                 {:id :edit :label "Edit"}]
        entities [{:id :user :label "User"}
                  {:id :payment-method :label "Payment Method"}
                  {:id :category :label "Category"}
                  {:id :vendor :label "vendor"}]
        action (reagent/atom :add)
        entity (reagent/atom :vendor)]
    (fn []
      (if @user
        (let [admin? (:user/isAdmin @user)]
          [re-com/v-box
           :gap "1rem"
           :children [[panel-title "Schema Editor"]
                      [labelled "Schema change" nil
                       [re-com/h-box
                        :gap "0.5rem"
                        :children [[re-com/single-dropdown :width  "5rem"
                                    :choices actions
                                    :model action
                                    :on-change #(reset! action %)]
                                   [re-com/single-dropdown :width "11rem"
                                    :choices entities
                                    :model entity
                                    :on-change #(reset! entity %)]]]]
                      [panel-subtitle (str (utils/get-at actions :id @action :label) " "
                                           (utils/get-at entities :id @entity :label))]
                      (case @action
                        :add (case @entity
                               :user (if admin? [add-user] [unavailable])
                               :payment-method [add-payment-method]
                               :category [add-category]
                               :vendor [add-vendor]
                               [:div "ERROR??"])
                        :edit [:div "NYI"])]])
        [login-panel]))))

(defn about-panel []
  (let [about-server (re-frame/subscribe [:about-server])]
    (fn []
      [re-com/v-box
       :gap "1rem"
       :children [(panel-title "About")
                  [:div
                   [:p "Third iteration of a simple receipts management program."]
                   [:p "This time, my focus is on learning Vase, Datomic, and Pedestal."]
                   [:h4 "Status"]
                   [:p (goog.string/format "Currently holding %d purchases." (:purchases-count @about-server))]
                   [:h4 (goog.string/format "Version %s" (:version @about-server))]
                   [:h5 "Includes libraries:"]
                   [:small
                    [:ul
                     (map (fn [[dependency version]]
                            ^{:key dependency}[:li (goog.string/format "%s:%s" dependency version)])
                          (sort-by first (:dependencies @about-server)))]]
                   [:p
                    [:em "Copyright (c) 2017, David Goldfarb <deg@degel.com>"]
                    [:br]
                    [:em "Portions copyright 2013-2016."]]]]])))

(def date-format (time-format/formatter "ddMMMyy"))
(def time-format (time-format/formatter "HH:mm:ss"))

(defn field-output [field value]
  (case field
    :forWhom (str/join ", " value)
    :date (time-format/unparse date-format (time-coerce/to-date-time value))
    :time (time-format/unparse time-format (time-coerce/to-date-time value))
    :price (let [[currency amount] value
                 symbol (case currency
                          "USD" "$"
                          "EU" "\u20AC"
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
                 ["Paid by" "Date" "Time" "Price" "Category" "Vendor" "Comment" "For Whom"])]]
   [:tbody (map (fn [{:purchase/keys [paymentMethod date currency price category vendor comment forWhom] :as purchase}]
                  (let [row-id (:db/id purchase)
                        id-fn (partial str row-id "-")]
                    ^{:key row-id} [:tr
                                    (history-cell id-fn :paymentMethod paymentMethod)
                                    (history-cell id-fn :date date)
                                    (history-cell id-fn :time date)
                                    (history-cell id-fn :price [currency price])
                                    (history-cell id-fn :category category)
                                    (history-cell id-fn :vendor vendor)
                                    (history-cell id-fn :comment comment)
                                    (history-cell id-fn :forWhom forWhom)]))
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
  (let [user (re-frame/subscribe [:user])
        history (re-frame/subscribe [:history])
        csv (re-frame/subscribe [:history-csv])]
    (fn []
      (if @user
        (let [consumer? (:user/isConsumer @user)
              editor? (:user/isEditor @user)
              admin? (:user/isAdmin @user)]
          [re-com/v-box
           :gap "1rem"
           :children [(panel-title "History")
                      [history-table @history]
                      (when admin?
                        [history-csv @csv])
                      [re-com/h-box
                       :children [(button [:get-history] "Refresh History" "Load history from server")]]]])
        [login-panel]))))

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
  (let [server (re-frame/subscribe [:server])
        user (re-frame/subscribe [:user])
        schema (re-frame/subscribe [:schema])
        verbose? (reagent/atom false)]
    (fn []
      (let [email (:user/email @user)
            consumer? (:user/isConsumer @user)
            editor? (:user/isEditor @user)
            admin? (:user/isAdmin @user)]
        [re-com/v-box
         :gap "1rem"
         :children [(panel-title "Setup")
                    [re-com/h-box
                     :gap "1rem"
                     :children [[:span (str "Logged in as " (or email "[UNKNOWN USER]"))]
                                [button [:logout] "Logout" "Logout from server"]]]
                    (section-title "Developer tools")
                    (if admin?
                      [re-com/h-box
                                  :gap "1rem"
                                  :children [[re-com/v-box
                                              :children [[re-com/label :label "Server cold init:"]
                                                         (button [:preload-base]
                                                                 "Preload database"
                                                                 "Install initial DB (you should not need this)")]]
                                             [re-com/v-box
                                              :children [[re-com/label :label "Choose server:"]
                                                         [re-com/single-dropdown :choices [{:id :production :label "production"}
                                                                                           {:id :development :label "development"}]
                                                          :width "12rem"
                                                          :model @server
                                                          :on-change #(re-frame/dispatch [:set-server %])]]]]]
                      [unavailable])
                    (section-title "Schema")
                    (re-com/checkbox
                     :model verbose?
                     :on-change #(reset! verbose? %)
                     :label "Show all?")
                    (when admin?
                      (formatted-schema "Users" (:users @schema) (not @verbose?)))
                    (formatted-schema "Payment Methods" (:payment-methods @schema) (not @verbose?))
                    (formatted-schema "Currencies" (:currencies @schema) (not @verbose?))
                    (formatted-schema "Categories" (:categories @schema) (not @verbose?))
                    (formatted-schema "Vendors" (:vendors @schema) (not @verbose?))]]))))

(defn setup-panel []
  (let [credentials (re-frame/subscribe [:credentials])]
    (fn []
      (if @credentials [config-panel] [login-panel]))))

(defn wrap-page [page]
  [re-com/border
   :width "90vw"
   :margin "1vmin"
   :border "0.2vmin solid lightgrey"
   :padding "1rem"
   :child page])

(def tabs [{:id :home    :label "receipt"   :panel (wrap-page [home-panel])}
           {:id :edit    :label "edit"      :panel (wrap-page [edit-panel])}
           {:id :history :label "history"   :panel (wrap-page [history-panel])}
           {:id :about   :label "about"     :panel (wrap-page [about-panel])}
           {:id :setup   :label "setup"     :panel (wrap-page [setup-panel])}])

(defn tab-panel []
  (let [page (re-frame/subscribe [:page])]
    (fn []
      (:panel (or (first (filter #(= (:id %) @page)
                                 tabs))
                  :home-panel)))))

(defn tabs-row []
  (let [page (re-frame/subscribe [:page])
        server (re-frame/subscribe [:server])]
    (fn []
      [re-com/horizontal-pill-tabs
       :tabs tabs
       :model (or @page :home)
       :on-change #(routes/goto-page % @server)])))

(defn main-panel []
  (let [schema (re-frame/subscribe [:schema])]
    (fn []
      ;; [TODO] This gets the schema once when the program starts. This is probably wrong:
      ;; - I don't know if this is called at the best time or place
      ;; - No mechanism to get changed schema; especially, e.g., if a client is open for many days
      (re-frame/dispatch [:update-credentials])
      [re-com/v-box
       :children [[app-title]
                  [tabs-row]
                  [tab-panel]]])))
