;;; Author: David Goldfarb (deg@degel.com)
;;; Copyright (c) 2017, David Goldfarb

(ns receipts-server.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [com.cognitect.vase :as vase]
            [receipts-server.interceptors]
            [receipts-server.render]))

;;;- (defn about-page
;;;-   [request]
;;;-   (ring-resp/response (format "Clojure %s - served from %s"
;;;-                               (clojure-version)
;;;-                               (route/url-for ::about-page))))
;;;- 
;;;- (defn home-page
;;;-   [request]
;;;-   (ring-resp/response "Hello World!"))
;;;- 
;;;- ;; Defines "/" and "/about" routes with their associated :get handlers.
;;;- ;; The interceptors defined after the verb map (e.g., {:get home-page}
;;;- ;; apply to / and its children (/about).
;;;- (def common-interceptors [(body-params/body-params) http/html-body])
;;;- 
;;;- ;; Tabular routes
;;;- (def routes #{["/" :get (conj common-interceptors `home-page)]
;;;-               ["/about" :get (conj common-interceptors `about-page)]})
(def routes #{})

(def service
  {:env :prod
   ;; You can bring your own non-default interceptors. Make
   ;; sure you include routing and set it up right for
   ;; dev-mode. If you do, many other keys for configuring
   ;; default interceptors will be ignored.
   ;; ::http/interceptors []

   ;; CORS allowed origins
   ::http/allowed-origins ["http://localhost:3449" "http://lightsail-1.degel.com"]

   ::route-set routes
   ::vase/api-root "/api"
   ::vase/spec-resources ["receipts-server_service.edn"]

   ;; Root for resource interceptor that is available by default.
   ::http/resource-path "/public"

   ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
   ::http/type :jetty
   ;;::http/host "localhost"
   ::http/port 8080
   ;; Options to pass to the container (Jetty)
   ::http/container-options {:h2c? true
                             :h2? false
                             ;:keystore "test/hp/keystore.jks"
                             ;:key-password "password"
                             ;:ssl-port 8443
                             :ssl? false}})

