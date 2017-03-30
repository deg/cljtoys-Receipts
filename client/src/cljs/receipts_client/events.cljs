(ns receipts-client.events
  (:require  [ajax.core :as ajax]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.db :as db]))

(def api-root "http://localhost:8080/api/receipts-server/v1/")
(def api-timeout 5000)

(re-frame/reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(defn api-request [method api params on-success]
  {:method method
   :uri (str api-root api)
   :params (if (or (= method :get)
                   (:payload params))
             params
             {:payload [params]})
   :timeout api-timeout
   :format (ajax/json-request-format)
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success on-success
   :on-failure [:process-failure]})

;; [TODO] Belongs in forthcoming utils library. Cribbed from photovu/src/cljc
(defn assoc-if
  "Like assoc, but takes pairs as a map, and only assocs non-nil values"
  [m kvs]
  (into m (remove (comp nil? val) kvs)))

(def get-request (partial api-request :get))
(def post-request (partial api-request :post))
(def delete-request (partial api-request :delete))

;;; [TODO] Temp, until we've shared the namespaces with the client

(defn- User-request [{:keys [name abbrev email sysAdmin? editor? consumer?]}]
  (assoc-if {}
    {"receipts.user/name" name
     "receipts.user/abbrev" abbrev
     "receipts.user/email" email
     "receipts.user/isSysAdmin" sysAdmin?
     "receipts.user/isEditor" editor?
     "receipts.user/isConsumer" consumer?}))

(defn- Category-request [{:keys [name description]}]
  (assoc-if {}
    {"receipts.category/name" name
     "receipts.category/description" description}))

(defn- Vendor-request [{:keys [name description category]}]
  (assoc-if {}
    {"receipts.vendor/name" name
     "receipts.vendor/description" description
     "receipts.vendor/category" category}))

(defn- Payment-method-request [{:keys [name abbrev]}]
  (assoc-if {}
    {"receipts.paymentMethod/name" name
     "receipts.paymentMethod/abbrev" abbrev}))

(defn- Currency-request [{:keys [name abbrev]}]
  (assoc-if {}
    {"receipts.currency/name" name
     "receipts.currency/abbrev" abbrev}))

(defn- Purchase-request [{:keys [uid price currency category vendor paid-by date comment for-whom]}]
  (assoc-if {}
    {"receipts.purchase/uid" uid
     "receipts.purchase/price" price
     "receipts.purchase/currency" currency
     "receipts.purchase/category" category
     "receipts.purchase/vendor" vendor
     "receipts.purchase/paidBy" paid-by
     "receipts.purchase/date" date
     "receipts.purchase/comment" comment
     "receipts.purchase/forWhom" for-whom}))



;; [TODO] Refactor this repeated code into an engine

(defn get-user-request [{:keys [name abbrev email] :as params} on-success]
  (get-request "user" params on-success))

(defn post-user-request [{:keys [name abbrev email sysAdmin? editor? consumer?] :as params} on-success]
  (post-request "user" (User-request params) on-success))

(defn get-vendor-request [{:keys [name] :as params} on-success]
  (get-request "vendor" params on-success))

(defn post-vendor-request [{:keys [name description category] :as params} on-success]
  (post-request "vendor" (Vendor-request params) on-success))

(defn post-vendors-request [vendors on-success]
  (post-request "vendor" {:payload (mapv Vendor-request vendors)} on-success))

(defn get-category-request [{:keys [name] :as params} on-success]
  (get-request "category" params on-success))

(defn post-category-request [{:keys [name description] :as params} on-success]
  (post-request "category" (Category-request params) on-success))

(defn get-payment-method-request [{:keys [name abbrev] :as params} on-success]
  (get-request "paymentMethod" params on-success))

(defn post-payment-method-request [{:keys [name abbrev] :as params} on-success]
  (post-request "paymentMethod" (Payment-method-request params) on-success))

(defn get-currency-request [{:keys [name abbrev] :as params} on-success]
  (get-request "currency" params on-success))

(defn post-currency-request [{:keys [name abbrev] :as params} on-success]
  (post-request "currency" (Currency-request params) on-success))

(defn get-purchase-request [{:keys [uid price currency category vendor paid-by date comment for-whom] :as params} on-success]
  (get-request "purchase" params on-success))

(defn post-purchase-request [{:keys [uid price currency category vendor paid-by date comment for-whom] :as params} on-success]
  (post-request "purchase" (Purchase-request params) on-success))



;;; [TODO] The rest of this is for one-time initialization. Should move to a data file

(defn User [name abbrev email sysAdmin? editor? consumer?]
  [(post-user-request {:name name :abbrev abbrev :email email
                       :sysAdmin? sysAdmin? :editor? editor? :consumer? consumer?}
                      [:process-response])])

(defn- simple-vendor [name category]
  {:name name :description "" :category category})

(defn Vendor [category name]
  [(post-vendor-request (simple-vendor name category) [:process-response])])

(defn Category [category-name description vendor-names]
  (vector
   (post-category-request {:name category-name :description description} [:process-response])
   (let [vendor-requests (mapv #(simple-vendor % category-name) vendor-names)]
     (post-vendors-request vendor-requests [:process-response]))))

(defn Payment-method [name abbrev]
  [(post-payment-method-request {:name name :abbrev abbrev} [:process-response])])

(defn Currency [name abbrev]
  [(post-currency-request {:name name :abbrev abbrev} [:process-response])])

(defn Purchase [uid price currency category vendor paid-by date comment for-whom]
  [(post-purchase-request {:uid uid
                           :price price
                           :currency currency
                           :category category
                           :vendor vendor
                           :paid paid-by
                           :date date
                           :comment comment
                           :for for-whom}
                          [:process-response])])


(re-frame/reg-event-fx
 :preload-base
  (fn preload-base
    [{db :db} stuff]
    {:http-xhrio (concat
                  (User "David Goldfarb"    "D"       "deg@degel.com"                true  true  true)
                  (User "Heidi Brun"        "H"       "hmb@goldfarb-family.com"      false true  true)
                  (User "Aviva Goldfarb"    "A"       "aviva@goldfarb-family.com"    false true  true)
                  (User "Shoshana Goldfarb" "S"       "shoshana@goldfarb-family.com" false true  true)
                  (User "Netzach Menashe"   "Netzach" nil                            false false true)
                  (User "Heidi Brun Assoc." "Netzach" nil                            false false true)
                  (User "Degel"             "Degel"   nil                            false false true)
                  (Payment-method "Cash" "Cash")
                  (Payment-method "H Shufersal" "v9949")
                  (Payment-method "H UBank" "v9457-5760")
                  (Payment-method "D Shufersal" "v0223")
                  (Payment-method "D UBank" "v3732")
                  (Payment-method "AARP -5692" "v5692")
                  (Payment-method "Fid Debit -9835" "v9835")
                  (Payment-method "Fid MC -5331" "mc5331")
                  (Currency "US Dollars" "USD")
                  (Currency "Euros" "EU")
                  (Currency "GB Pounds" "GBP")
                  (Currency "New Israeli Shekels" "NIS")
                  (Category "Books" "Books, e-books, other media"
                            ["Masada" "Sefarim v'od" "Steimatzky" "Tzomet Sefarim"])
                  (Category "Car" "Car, parts, and service"
                            ["Parking" "Paz" "Puncheria Yossi" "Subaru"])
                  (Category "Charity" "Charitable donations, etc."
                            ["Beit Hagalgalim" "Deaf group" "Hakol Lashulchan" "Ima Ani Re'ev" "Mouth or foot painting artists"
                             "Swim4Sadna"])
                  (Category "Cleaning" "Housecleaning"
                            ["Daisy"])
                  (Category "Clothing" "Clothing, shoes, accessories"
                            ["200 meter fashion" "Amnon shoes" "Autenti" "Bazaar Strauss" "Clarks Shoes" "Dry cleaner" "fox"
                             "Glik" "H&O" "Hamachsan" "Hige IQ" "Kosher Casual" "Lili Shoes" "Lior kids clothing"
                             "Macbeset Hagiva" "Matias Matar Club Ofna" "Matok fashion" "Mekimi" "Olam Habeged" "Onyx Shoes"
                             "Pinuk" "Ricochet" "Shop Center" "Sisna fashion" "Super Shoe" "Tamnoon" "Zara"])
                  (Category "Dogs" "Food, supplies, and doctors for dogs"
                            ["Chayot HaBayit" "Dod Moshe" "Vet"])
                  (Category "Entertainment" "Movies, shows, and other entertainment"
                            ["Al Derech Burma" "BS Chamber Music Series" "Ba B'tov" "Bowling Modiin" "Cinema City"
                             "Country Beit Shemesh" "Disc Club" "Ezra" "Globus Max" "Mesilat Zion pool" "Nitzanim Beach"
                             "Rav Chen Modiin" "Yamit 2000"])
                  (Category "Food" "Non-restaurant food"
                            ["Alonit" "Angel" "Benny's" "Café Neeman" "Co-op" "Kanyon Mamtakim" "Kimat Chinam" "Mister Zol"
                             "Naomi's Cookies" "Osher ad" "Paz Yellow" "Pommeranz" "Shufersal deal" "Shufersal online"
                             "Super Aviv" "Super Hatzlacha" "SuperPharm" "Supersol Deal"])
                  (Category "Garden" "Tools, supplies, and services for home garden"
                            ["Beit Shemesh" "Hapina Hayeroka" "Mishtelet Habayit" "Nursery Chatzer Hamashtela"
                             "Richard's Flower Shop"])
                  (Category "Gift" "Gifts for other people"
                            ["Devarim Yafim" "Kangaroo" "Kfar HaShaashuim" "Magnolia" "Tachshit Plus"])
                  (Category "Health" "Medicines, and other health products"
                            ["Arthur's Pharmacy" "Chaya Shames" "Dr. Metz" "Hadassa SHaRaP" "Hamashbir LeTzarchan"
                             "Meuchedet RBS drugstore" "Meuchedet" "Meuhedet drugstore" "Optika Ayin Tov" "Optika Halperin"
                             "Optika Speed" "Optiko Petel-Natali" "Pharma shaul" "Shaarei Tzedek" "SuperPharm" "Terem"])
                  (Category "Home" "Catch-all for anything in home"
                            ["Ashley Coleman" "Ben Harush" "Big Deal" "Buy2" "Chana Weiss" "City Shop" "Cobbler"
                             "Devarim Yafim" "Ehud Exterminator" "Graphos" "Gris" "Hakol Labait RBS" "Hakol lashulchan"
                             "Hamashbir LaTzarchan" "Hidur" "Keter" "Kol Bo Yehezkel" "Kolbo Ohel Zahav" "Lighting store"
                             "Limor Zakuto" "M S Photos" "Machsanei Hakol LaShulchan" "Marvadim" "Masada" "Office Depot"
                             "Post Office" "Reuven Fabrics" "Sefarim v'od (R R Shivuk)" "Super Binyan" "Tachshit Plus"
                             "Tza'atzu'ei Hagiva" "Yossi Zadiki"])
                  (Category "Jewelry" "Jewelry"
                            ["Magnolia" "Tachshit plus"])
                  (Category "Kids" "Stuff for kids, education"
                            ["Beit Shemesh" "Bnei Akiva" "Conservatory" "Ezra" "Library" "Matnas" "Moadon Sport RBS"
                             "Naomi Ocean" "Pelech" "S'farim V'od" "Devarim Yafim" "HaPirate HaAdom" "Kangaroo"
                             "Kfar HaShaashuim" "Red Pirate"])
                  (Category "Restaurant" "Meals at restaurants"
                            ["Aldo" "AM:PM" "Aroma" "Big Apple Pizza" "Burger's Bar" "Cafe Bagels" "Cafe Cafe"
                             "Cafe  Greg" "Cafe Neeman" "Canela" "Chalav u'dvash" "El Baryo" "Felafal Ahava" "Gachalim"
                             "Japan-Japan" "Levi's Pizza" "McDonalds" "Marvad hakesmim" "Mercaz HaPizza" "Olla" "Pikansin"
                             "Pizza Hut" "Sashimi" "Sbarro" "Shwarma Skouri" "Tom's Place"])
                  (Category "Tax" "Accounting servicees and tax payments"
                            ["meches" "Royale Schoenbrun"])
                  (Category "Travel" "Airfare, bus, taxi, and other travel-related"
                            ["Bus" "Taxi" "Train"])
                  (Purchase "D-1002" 12.34 "USD" "Travel" "Bus" "D" "1985" "Test bus ride" "D")
                  (Purchase "D-1003" 12.34 "NIS" "Home" "Ashley Coleman" "D" "1985-04-12T23:20:50.52Z" "Test 2" "D"))
     :db  (assoc db :loading? true)}))


(re-frame/reg-event-db
 :process-response
 (fn process-response [db result]
   (prn "Got response: " result)
   db))

(re-frame/reg-event-db
 :process-failure
 (fn process-failure [db & result]
   (prn "Got failure: " result)
   db))
