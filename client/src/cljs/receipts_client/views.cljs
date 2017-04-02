(ns receipts-client.views
  (:require
   [cljs-time.core :as time]
   [cljs-time.format :as time-format]
   [cljs-time.coerce :as time-coerce]
   [clojure.string :as str]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [re-com.core :as re-com]
   [receipts-client.routes :as routes]))


;; home

(defn app-title []
  (let [name (re-frame/subscribe [:name])]
    (fn []
      [re-com/title
       :label @name
       :level :level1])))

(defn panel-title [label]
  [re-com/title :label label :level :level2])

(defn preload-button []
  [re-com/button
   :label "Preload database"
   :tooltip "Install initial DB (you should not need this)"
   :on-click #(re-frame/dispatch [:preload-base])])

(defn schema-button []
  [re-com/button
   :label "Get DB Schema"
   :tooltip "Learn DB schema"
   :on-click #(re-frame/dispatch [:get-base])])

(defn history-button []
  [re-com/button
   :label "Get History"
   :tooltip "yadda yadda"
   :on-click #(re-frame/dispatch [:get-history])])

(defn labelled [label component]
  [re-com/h-box
   :width "100%"
   :gap "1em"
   :children [[re-com/label :width "8em ":label (str label (if (str/blank? label) "" ": "))]
              component]])

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

(defn receipt-page []
  (let [current-receipt (re-frame/subscribe [:current-receipt])
        vendors (re-frame/subscribe [:vendors])]
    (fn []
      [re-com/v-box
       :gap "3px"
       :children
       [[labelled "Paid by"
         [dropdown :multiple? false
          :field-key :payment-method
          :subs-key :payment-methods
          :schema-key :receipts.paymentMethod/name]]
        [labelled "Date" [date-time-picker
                          :model (or (:date @current-receipt) (time/now))
                          :on-change #(re-frame/dispatch [:edit-current-receipt :date %])]]
        [labelled "Price" [re-com/input-text
                           :model (or (:price @current-receipt) "0.00")
                           :on-change #(re-frame/dispatch [:edit-current-receipt :price %])
                           :attr {:type "number"
                                  :step "0.01"}]]
        [labelled "Category" [dropdown :multiple? false
                              :field-key :category
                              :subs-key :categories
                              :schema-key :receipts.category/name]]
        [labelled "Vendor" [dropdown :multiple? false
                            :field-key :vendor
                            :subs-key :vendors
                            :schema-key :receipts.vendor/name
                            :filter-fn #(some #{(:category @current-receipt)} (:receipts.vendor/category %))]]
        [labelled "Comment" [re-com/input-text
                             :model (or (:comment @current-receipt) "")
                             :on-change #(re-frame/dispatch [:edit-current-receipt :comment %])
                             :attr {:type "text"}]]
        [labelled "For Whom" [dropdown :multiple? true
                              :field-key :for-whom
                              :subs-key :users
                              :schema-label-key :receipts.user/name
                              :schema-id-key :receipts.user/abbrev]]
        [re-com/gap :size "8px"]
        [re-com/h-box
         :justify :center
         :children
         [[re-com/button
           :disabled? false #_(not (valid-receipt (get-state [:current-receipt])))
           :on-click #(prn "NYI") ;#(submit-receipt (get-state [:current-receipt]))
           :label "Submit Receipt"]]]]])))

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
    :for-whom (str/join ", " value)
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
   [:tbody (map (fn [{:receipts.purchase/keys [paidBy date currency price category vendor comment forWhom] :as purchase}]
                  (let [row-id (:db/id purchase)
                        id-fn (partial str row-id "-")]
                    ^{:key row-id} [:tr
                                    (history-cell id-fn :paid-by paidBy)
                                    (history-cell id-fn :date date)
                                    (history-cell id-fn :time date)
                                    (history-cell id-fn :price [currency price])
                                    (history-cell id-fn :category category)
                                    (history-cell id-fn :vendor vendor)
                                    (history-cell id-fn :comment comment)
                                    (history-cell id-fn :for-whom forWhom)]))
                purchases)]])

(defn history-panel []
  (let [history (re-frame/subscribe [:history])]
    (fn []
      [re-com/v-box
       :gap "1em"
       :children [(panel-title "History")
                  [history-button]
                  [history-table @history]]])))

(defn setup-panel []
  [re-com/v-box
   :gap "1em"
   :children [(panel-title "Setup")
              [preload-button]
              [schema-button]]])

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
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      (:panel (or (first (filter #(= (:id %) @active-panel)
                                 tabs))
                  :home-panel)))))

(defn tabs-row []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [re-com/horizontal-pill-tabs
       :tabs tabs
       :model (or @active-panel :home)
       :on-change #(routes/goto-page (name %))])))

(defn main-panel []
  [re-com/v-box
   :height "100%"
   :children [[app-title]
              [tabs-row]
              [tab-panel]]] )

