;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.preload
  (:require  [ajax.core :as ajax]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]))

;; [TODO] Belongs in forthcoming utils library. Cribbed from photovu/src/cljc
(defn assoc-if
  "Like assoc, but takes pairs as a map, and only assocs non-nil values"
  [m kvs]
  (into m (remove (comp nil? val) kvs)))

(defn User [server name abbrev email sysAdmin? editor? consumer?]
  [(api/post-user-request server
    (assoc-if {}
      {:user/name name :user/abbrev abbrev :user/email email
       :user/sysAdmin? sysAdmin? :user/editor? editor? :user/consumer? consumer?})
    [:process-response])])

(defn- simple-vendor [name category]
  {:vendor/name name :description "" :vendor/category category})

(defn Vendor [server category name]
  [(api/post-vendor-request server (simple-vendor name category) [:process-response])])

(defn Category [server category-name description vendor-names]
  (vector
   (api/post-category-request server {:category/name category-name :category/description description} [:process-response])
   (let [vendor-requests (mapv #(simple-vendor % category-name) vendor-names)]
     (api/post-vendors-request server vendor-requests [:process-response]))))

(defn Payment-method [server name abbrev]
  [(api/post-payment-method-request server {:paymentMethod/name name :paymentMethod/abbrev abbrev} [:process-response])])

(defn Currency [server name abbrev]
  [(api/post-currency-request server {:currency/name name :currency/abbrev abbrev} [:process-response])])

(defn Purchase [server uid price currency category vendor paid-by date comment for-whom]
  [(api/post-purchase-request server {:purchase/uid uid
                                      :purchase/price price
                                      :purchase/currency currency
                                      :purchase/category category
                                      :purchase/vendor vendor
                                      :purchase/paid-by paid-by
                                      :purchase/date date
                                      :purchase/comment comment
                                      :purchase/for-whom for-whom}
                              [:process-response])])

(defn initial-data [server]
  (concat
   (User server "David Goldfarb"    "D"       "deg@degel.com"                true  true  true)
   (User server "Heidi Brun"        "H"       "hmb@goldfarb-family.com"      false true  true)
   (User server "Aviva Goldfarb"    "A"       "aviva@goldfarb-family.com"    false true  true)
   (User server "Shoshana Goldfarb" "S"       "shoshana@goldfarb-family.com" false true  true)
   (User server "Netzach Menashe"   "Netzach" nil                            false false true)
   (User server "Heidi Brun Assoc." "HBA"     nil                            false false true)
   (User server "Degel"             "Degel"   nil                            false false true)
   (Payment-method server "Cash" "Cash")
   (Payment-method server "H Shufersal" "v9949")
   (Payment-method server "H UBank" "v9457-5760")
   (Payment-method server "D Shufersal" "v0223")
   (Payment-method server "D UBank" "v3732")
   (Payment-method server "AARP -5692" "v5692")
   (Payment-method server "Fid Debit -9835" "v9835")
   (Payment-method server "Fid MC -5331" "mc5331")
   (Currency server "US Dollars" "USD")
   (Currency server "Euros" "EU")
   (Currency server "GB Pounds" "GBP")
   (Currency server "New Israeli Shekels" "NIS")
   (Category server "Books" "Books, e-books, other media"
             ["Masada" "Sefarim v'od" "Steimatzky" "Tzomet Sefarim"])
   (Category server "Car" "Car, parts, and service"
             ["Parking" "Paz" "Puncheria Yossi" "Subaru"])
   (Category server "Charity" "Charitable donations, etc."
             ["Beit Hagalgalim" "Deaf group" "Hakol Lashulchan" "Ima Ani Re'ev" "Mouth or foot painting artists"
              "Swim4Sadna"])
   (Category server "Cleaning" "Housecleaning"
             ["Daisy"])
   (Category server "Clothing" "Clothing, shoes, accessories"
             ["200 meter fashion" "Amnon shoes" "Autenti" "Bazaar Strauss" "Clarks Shoes" "Dry cleaner" "fox"
              "Glik" "H&O" "Hamachsan" "Hige IQ" "Kosher Casual" "Lili Shoes" "Lior kids clothing"
              "Macbeset Hagiva" "Matias Matar Club Ofna" "Matok fashion" "Mekimi" "Olam Habeged" "Onyx Shoes"
              "Pinuk" "Ricochet" "Shop Center" "Sisna fashion" "Super Shoe" "Tamnoon" "Zara"])
   (Category server "Dogs" "Food, supplies, and doctors for dogs"
             ["Chayot HaBayit" "Dod Moshe" "Vet"])
   (Category server "Entertainment" "Movies, shows, and other entertainment"
             ["Al Derech Burma" "BS Chamber Music Series" "Ba B'tov" "Bowling Modiin" "Cinema City"
              "Country Beit Shemesh" "Disc Club" "Ezra" "Globus Max" "Mesilat Zion pool" "Nitzanim Beach"
              "Rav Chen Modiin" "Yamit 2000"])
   (Category server "Food" "Non-restaurant food"
             ["Alonit" "Angel" "Benny's" "Café Neeman" "Co-op" "Kanyon Mamtakim" "Kimat Chinam" "Mister Zol"
              "Naomi's Cookies" "Osher ad" "Paz Yellow" "Pommeranz" "Shufersal deal" "Shufersal online"
              "Super Aviv" "Super Hatzlacha" "SuperPharm" "Supersol Deal"])
   (Category server "Garden" "Tools, supplies, and services for home garden"
             ["Beit Shemesh" "Hapina Hayeroka" "Mishtelet Habayit" "Nursery Chatzer Hamashtela"
              "Richard's Flower Shop"])
   (Category server "Gift" "Gifts for other people"
             ["Devarim Yafim" "Kangaroo" "Kfar HaShaashuim" "Magnolia" "Tachshit Plus"])
   (Category server "Health" "Medicines, and other health products"
             ["Arthur's Pharmacy" "Chaya Shames" "Dr. Metz" "Hadassa SHaRaP" "Hamashbir LeTzarchan"
              "Meuchedet RBS drugstore" "Meuchedet" "Meuhedet drugstore" "Optika Ayin Tov" "Optika Halperin"
              "Optika Speed" "Optiko Petel-Natali" "Pharma shaul" "Shaarei Tzedek" "SuperPharm" "Terem"])
   (Category server "Home" "Catch-all for anything in home"
             ["Ashley Coleman" "Ben Harush" "Big Deal" "Buy2" "Chana Weiss" "City Shop" "Cobbler"
              "Devarim Yafim" "Ehud Exterminator" "Graphos" "Gris" "Hakol Labait RBS" "Hakol lashulchan"
              "Hamashbir LaTzarchan" "Hidur" "Keter" "Kol Bo Yehezkel" "Kolbo Ohel Zahav" "Lighting store"
              "Limor Zakuto" "M S Photos" "Machsanei Hakol LaShulchan" "Marvadim" "Masada" "Office Depot"
              "Post Office" "Reuven Fabrics" "Sefarim v'od (R R Shivuk)" "Super Binyan" "Tachshit Plus"
              "Tza'atzu'ei Hagiva" "Yossi Zadiki"])
   (Category server "Jewelry" "Jewelry"
             ["Magnolia" "Tachshit plus"])
   (Category server "Kids" "Stuff for kids, education"
             ["Beit Shemesh" "Bnei Akiva" "Conservatory" "Ezra" "Library" "Matnas" "Moadon Sport RBS"
              "Naomi Ocean" "Pelech" "S'farim V'od" "Devarim Yafim" "HaPirate HaAdom" "Kangaroo"
              "Kfar HaShaashuim" "Red Pirate"])
   (Category server "Restaurant" "Meals at restaurants"
             ["Aldo" "AM:PM" "Aroma" "Big Apple Pizza" "Burger's Bar" "Cafe Bagels" "Cafe Cafe"
              "Cafe  Greg" "Cafe Neeman" "Canela" "Chalav u'dvash" "El Baryo" "Felafal Ahava" "Gachalim"
              "Japan-Japan" "Levi's Pizza" "McDonalds" "Marvad hakesmim" "Mercaz HaPizza" "Olla" "Pikansin"
              "Pizza Hut" "Sashimi" "Sbarro" "Shwarma Skouri" "Tom's Place"])
   (Category server "Tax" "Accounting servicees and tax payments"
             ["meches" "Royale Schoenbrun"])
   (Category server "Travel" "Airfare, bus, taxi, and other travel-related"
             ["Bus" "Taxi" "Train"])
   (Purchase server "D-1001" 12.01 "NIS" "Home" "Ashley Coleman" "v9949" "1985-04-12T23:20:50.52Z" "Test 2" "D")
   (Purchase server "D-1002" 12.02 "USD" "Travel" "Bus" "Cash" "1990" "Test bus ride" ["D" "Degel"])
   (Purchase server "D-1003" 12.03 "EU" "Restaurant" "La Chateau Fumanchu" "v5692" "1987-04-12T23:20:50.52Z" "Euro test" "D")
   (Purchase server "D-1004" 12.04 "GBP" "Home" "Cyrus McDuffey" "mc5331" "1982-04-12T23:15:50.52Z" "Another" "D")))
