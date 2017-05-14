;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-client.preload
  (:require  [ajax.core :as ajax]
             [day8.re-frame.http-fx]
             [re-frame.core :as re-frame]
             [receipts-client.api-client :as api]
             [receipts-client.db :as db]
             [receipts-client.utils :as utils]))


(defn post-multi-request [api packer]
  (fn [server credentials requests]
    [(api/post-request {:server server
                        :api api
                        :params {:credentials credentials
                                 :payload (mapv api/string-keyed
                                                (map packer requests))}
                        :on-success [:get-schema api]})]))

(def Users
  (post-multi-request "user"
                      (fn [[abbrev name email password admin? editor? consumer?]]
                        (utils/assoc-if {}
                          {:user/abbrev     abbrev
                           :user/name       name
                           :user/email      email
                           :user/password   password
                           :user/isAdmin    admin?
                           :user/isEditor   editor?
                           :user/isConsumer consumer?}))))

(def Currencies
  (post-multi-request "currency"
                      (fn [[name abbrev]] {:currency/name name :currency/abbrev abbrev})))

(def Payment-methods
  (post-multi-request "paymentMethod"
                      (fn [[name abbrev]] {:paymentMethod/name name :paymentMethod/abbrev abbrev})))

(def Categories
  (post-multi-request "category"
                      (fn [[name description]] {:category/name name :category/description description})))

(defn assign-vendors [categories-and-vendors]
  (apply concat
         (mapv (fn [[category vendors]]
                 (mapv (fn [vendor]
                         [vendor category])
                       vendors))
               categories-and-vendors)))

(def Vendors
  (post-multi-request "vendor"
                      (fn [[name category]] {:vendor/name name :description "" :vendor/category category})))

(def Purchases
  (post-multi-request "purchase"
                      (fn [[uid payment-method date currency price category vendor comment for-whom]]
                        {:purchase/uid uid
                         :purchase/paymentMethod payment-method
                         :purchase/date date
                         :purchase/currency currency
                         :purchase/price price
                         :purchase/category category
                         :purchase/vendor vendor
                         :purchase/comment comment
                         :purchase/forWhom for-whom})))



(defn initial-data [server credentials]
  (concat
   (Users
    server credentials
    [["D"       "David Goldfarb"    "deg@degel.com"                "abc" true  true  true]
     ["H"       "Heidi Brun"        "hmb@goldfarb-family.com"      "abc" false true  true]
     ["A"       "Aviva Goldfarb"    "aviva@goldfarb-family.com"    "abc" false true  true]
     ["S"       "Shoshana Goldfarb" "shoshana@goldfarb-family.com" "abc" false true  true]
     ["Netzach" "Netzach Menashe"   nil                             nil  false false true]
     ["HBA"     "Heidi Brun Assoc." nil                             nil  false false true]
     ["Degel"   "Degel"             nil                             nil  false false true]])
   (Payment-methods
    server credentials
    [["Cash" "Cash"]
     ["H Shufersal" "v9949"]
     ["H UBank" "v9457-5760"]
     ["D Shufersal" "v0223"]
     ["D UBank" "v3732"]
     ["AARP -5692" "v5692"]
     ["Fid Debit -9835" "v9835"]
     ["Fid MC -5331" "mc5331"]])
   (Currencies
    server credentials
    [["US Dollars" "USD"]
     ["Euros" "EU"]
     ["GB Pounds" "GBP"]
     ["New Israeli Shekels" "NIS"]])
   (Categories
    server credentials
    [["Books"         "Books, e-books, other media"]
     ["Car"           "Car, parts, and service"]
     ["Charity"       "Charitable donations, etc."]
     ["Cleaning"      "Housecleaning"]
     ["Clothing"      "Clothing, shoes, accessories"]
     ["Dogs"          "Food, supplies, and doctors for dogs"]
     ["Entertainment" "Movies, shows, and other entertainment"]
     ["Food"          "Non-restaurant food"]
     ["Garden"        "Tools, supplies, and services for home garden"]
     ["Gift"          "Gifts for other people"]
     ["Health"        "Medicines, and other health products"]
     ["Home"          "Catch-all for anything in home"]
     ["Jewelry"       "Jewelry"]
     ["Kids"          "Stuff for kids, education"]
     ["Restaurant"    "Meals at restaurants"]
     ["Tax"           "Accounting servicees and tax payments"]
     ["Travel"        "Airfare, bus, taxi, and other travel-related"]])
   (Vendors
    server credentials
    (assign-vendors
     [["Books"
       ["Masada" "Sefarim v'od" "Steimatzky" "Tzomet Sefarim"]]
      ["Car"
       ["Parking" "Paz" "Puncheria Yossi" "Subaru"]]
      ["Charity"
       ["Beit Hagalgalim" "Deaf group" "Hakol Lashulchan" "Ima Ani Re'ev" "Mouth or foot painting artists"
        "Swim4Sadna"]]
      ["Cleaning"
       ["Daisy"]]
      ["Clothing"
       ["200 meter fashion" "Amnon shoes" "Autenti" "Bazaar Strauss" "Clarks Shoes" "Dry cleaner" "fox"
        "Glik" "H&O" "Hamachsan" "Hige IQ" "Kosher Casual" "Lili Shoes" "Lior kids clothing"
        "Macbeset Hagiva" "Matias Matar Club Ofna" "Matok fashion" "Mekimi" "Olam Habeged" "Onyx Shoes"
        "Pinuk" "Ricochet" "Shop Center" "Sisna fashion" "Super Shoe" "Tamnoon" "Zara"]]
      ["Dogs"
       ["Chayot HaBayit" "Dod Moshe" "Vet"]]
      ["Entertainment"
       ["Al Derech Burma" "BS Chamber Music Series" "Ba B'tov" "Bowling Modiin" "Cinema City"
        "Country Beit Shemesh" "Disc Club" "Ezra" "Globus Max" "Mesilat Zion pool" "Nitzanim Beach"
        "Rav Chen Modiin" "Yamit 2000"]]
      ["Food"
       ["Alonit" "Angel" "Benny's" "Café Neeman" "Co-op" "Kanyon Mamtakim" "Kimat Chinam" "Mister Zol"
        "Naomi's Cookies" "Osher ad" "Paz Yellow" "Pommeranz" "Shufersal deal" "Shufersal online"
        "Super Aviv" "Super Hatzlacha" "SuperPharm" "Supersol Deal"]]
      ["Garden"
       ["Beit Shemesh" "Hapina Hayeroka" "Mishtelet Habayit" "Nursery Chatzer Hamashtela"
        "Richard's Flower Shop"]]
      ["Gift"
       ["Devarim Yafim" "Kangaroo" "Kfar HaShaashuim" "Magnolia" "Tachshit Plus"]]
      ["Health"
       ["Arthur's Pharmacy" "Chaya Shames" "Dr. Metz" "Hadassa SHaRaP" "Hamashbir LeTzarchan"
        "Meuchedet RBS drugstore" "Meuchedet" "Meuhedet drugstore" "Optika Ayin Tov" "Optika Halperin"
        "Optika Speed" "Optiko Petel-Natali" "Pharma shaul" "Shaarei Tzedek" "SuperPharm" "Terem"]]
      ["Home"
       ["Ashley Coleman" "Ben Harush" "Big Deal" "Buy2" "Chana Weiss" "City Shop" "Cobbler"
        "Devarim Yafim" "Ehud Exterminator" "Graphos" "Gris" "Hakol Labait RBS" "Hakol lashulchan"
        "Hamashbir LaTzarchan" "Hidur" "Keter" "Kol Bo Yehezkel" "Kolbo Ohel Zahav" "Lighting store"
        "Limor Zakuto" "M S Photos" "Machsanei Hakol LaShulchan" "Marvadim" "Masada" "Office Depot"
        "Post Office" "Reuven Fabrics" "Sefarim v'od (R R Shivuk)" "Super Binyan" "Tachshit Plus"
        "Tza'atzu'ei Hagiva" "Yossi Zadiki"]]
      ["Jewelry"
       ["Magnolia" "Tachshit plus"]]
      ["Kids"
       ["Beit Shemesh" "Bnei Akiva" "Conservatory" "Ezra" "Library" "Matnas" "Moadon Sport RBS"
        "Naomi Ocean" "Pelech" "S'farim V'od" "Devarim Yafim" "HaPirate HaAdom" "Kangaroo"
        "Kfar HaShaashuim" "Red Pirate"]]
      ["Restaurant"
       ["Aldo" "AM:PM" "Aroma" "Big Apple Pizza" "Burger's Bar" "Cafe Bagels" "Cafe Cafe"
        "Cafe  Greg" "Cafe Neeman" "Canela" "Chalav u'dvash" "El Baryo" "Felafal Ahava" "Gachalim"
        "Japan-Japan" "Levi's Pizza" "McDonalds" "Marvad hakesmim" "Mercaz HaPizza" "Olla" "Pikansin"
        "Pizza Hut" "Sashimi" "Sbarro" "Shwarma Skouri" "Tom's Place"]]
      ["Tax"
       ["meches" "Royale Schoenbrun"]]
      ["Travel"
       ["Bus" "Taxi" "Train"]]]))
   (Purchases
    server credentials
    [["D-1001" "v9949" "1985-04-12T23:20:50.52Z" "NIS" 12.01 "Home" "Ashley Coleman" "Test 2" "D"]
     ["D-1002" "Cash" "1990" "USD" 12.02 "Travel" "Bus" "Test bus ride" ["D" "Degel"]]
     ["D-1003" "v5692" "1987-04-12T23:20:50.52Z" "EU" 12.03 "Restaurant" "La Chateau Fumanchu" "Euro test" "D"]
     ["D-1004" "mc5331" "1982-04-12T23:15:50.52Z" "GBP" 12.04 "Home" "Cyrus McDuffey" "Another" "D"]])))
