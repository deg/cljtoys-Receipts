;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.views
  (:require
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [cljs-time.coerce :as time-coerce]
   [clojure.string :as str]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [re-com.core :as re-com]
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
  [re-com/title :label label :level :level2])

(defn button [dispatch label tooltip]
  [re-com/button
   :label label
   :tooltip tooltip
   :on-click #(re-frame/dispatch dispatch)])

(defn labelled [label error component]
  (fn [label error component]
    [re-com/h-box
     :width "100%"
     :gap "1em"
     :children [[re-com/label
                 :class (if error "errmsg" "")
                 :width "8em "
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
     :gap "0.5rem"
     :align :center
     :children [(when show-multi-year?
                  [re-com/button
                   :label "<-5 yrs"
                   :class "btn-primary btn-sm"
                   :on-click #(do (swap! date-atom time/minus (time/years 5))
                                  (on-change (update-date-time model @date-atom nil)))])
                [re-com/datepicker-dropdown
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
     :width "15em"
     :model ((if multiple? (partial into #{}) identity)
             (field-key @current-receipt))
     :choices (sort-by :label (mapv (fn [{id schema-id-key
                                          label schema-label-key}]
                                      {:id id :label label})
                                    (if filter-fn
                                      (filter filter-fn @schema)
                                      @schema)))
     :on-change #(re-frame/dispatch [:edit-current-receipt field-key %])]))

;;; See https://funcool.github.io/struct/latest/
;;; [TODO] Replace with spec, asap.
(def complete-receipt
  {:purchase/paymentMethod [[struct/required :message "'Paid by' missing"] struct/string]
   :purchase/date [[struct/required :message "Please specify date"] struct/positive]
   :purchase/category [[struct/required :message  "Category missing"] struct/string]
   :purchase/vendor [[struct/required :message  "Choose vendor after specifying category"] struct/string]
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
         :gap "3px"
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
            :model (or (:purchase/comment @current-receipt) "")
            :on-change #(re-frame/dispatch [:edit-current-receipt :purchase/comment %])
            :attr {:type "text"}]]
          [labelled "For Whom"
           (:purchase/forWhom validation-errors)
           [dropdown :multiple? true
            :field-key :purchase/forWhom
            :subs-key :users
            :schema-label-key :user/name
            :schema-id-key :user/abbrev]]
          [re-com/gap :size "8px"]
          [re-com/h-box
           :justify :center
           :children
           [[re-com/button
             :disabled? (not (valid-receipt? @current-receipt))
             :on-click #(re-frame/dispatch [:submit-receipt @current-receipt])
             :label "Submit Receipt"]]]]]))))

(defn home-panel []
  [re-com/v-box
   :gap "1em"
   :children [(panel-title "New Receipt")
              [receipt-page]]])

(defn about-panel []
  [re-com/v-box
   :gap "1em"
   :children [(panel-title "About")]])

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

(defn history-panel []
  (let [history (re-frame/subscribe [:history])]
    (fn []
      [re-com/v-box
       :gap "1em"
       :children [(panel-title "History")
                  (button [:get-history] "Get History" "Load history from server")
                  [history-table @history]]])))

(defn setup-panel []
  (let [server (re-frame/subscribe [:server])]
    (fn []
      (let [this-server  (case @server :production "production" :development "development" "???")
            other-server (case @server :production "development" :development "production" "???")]
        [re-com/v-box
         :gap "1em"
         :children [(panel-title "Setup")
                    (button [:preload-base] "Preload database" "Install initial DB (you should not need this)")
                    (button [:toggle-server] (str "Toggle server (now " this-server ")")
                            (str "Switch to " other-server " server"))]]))))

(defn wrap-page [page]
  [re-com/border
   :margin "3px"
   :border "1px solid lightgrey"
   :padding "6px"
   :child page])

(def tabs [{:id :home    :label "home"    :panel (wrap-page [home-panel])}
           {:id :history :label "history" :panel (wrap-page [history-panel])}
           {:id :setup   :label "setup"   :panel (wrap-page [setup-panel])}
           {:id :about   :label "about"   :panel (wrap-page [about-panel])}])

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
       :on-change #(re-frame/dispatch [:set-page % @server])])))

(defn main-panel []
  (let [schema (re-frame/subscribe [:schema])]
    (fn []
      ;; [TODO] This gets the schema once when the program starts. This is probably wrong:
      ;; - I don't know if this is called at the best time or place
      ;; - No mechanism to get changed schema; especially, e.g., if a client is open for many days
      (re-frame/dispatch [:get-schema])
      [re-com/v-box
       :height "100%"
       :children [[app-title]
                  [tabs-row]
                  [tab-panel]]])) )

