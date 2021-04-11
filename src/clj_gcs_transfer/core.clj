(ns clj-gcs-transfer.core
  (:gen-class)
  (:require [clj-gcs-transfer.infrastructure.gcs-image-db.core :as gcs-image-db-infrastructure]
            [clj-gcs-transfer.interface.gateway.image-db.pics-service :as pics-service]
            [clj-gcs-transfer.interface.gateway.image-db.gcs.pics-service]

            [clj-gcs-transfer.utils :refer [err->> border-error]]

            [clojure.string]
            [environ.core :refer [env]]
            ;; router
            [reitit.ring :as ring]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [reitit.coercion.spec]
            [muuntaja.core :as m]
            [reitit.swagger :as swagger]
            [clojure.spec.alpha :as s]
            [clj-gcs-transfer.domain.pics :as pics-domain]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [javax.imageio ImageIO]))


;; interface
;; controller


(defn upload-http-> [input-data]
  (let [{:keys [parameters]} input-data
        {:keys [multipart]} parameters
        {:keys [file]} multipart
        input-model {:image-buffer (ImageIO/read (:tempfile file))
                     :blob (:filename file)}
        conformed-input-model (s/conform
                               ::pics-domain/upload-input input-model)]
    (if (not= ::s/invalid conformed-input-model)
      [conformed-input-model nil]
      [nil {:status 400 :body {:message "invalid input"}}])))

(defn download-http-> [input-data]
  (let [{:keys [parameters]} input-data
        {:keys [query]} parameters
        {:keys [filename]} query
        input-model {:blob filename}
        conformed-input-model (s/conform ::pics-domain/download-input input-model)]
    (if (not= ::s/invalid conformed-input-model)
      [conformed-input-model nil]
      [nil {:status 400 :body {:message "invalid input"}}])))

;; presenter
(defn ->upload-http [[output-model error]]
  (if (nil? error)
    (let [{:keys [blob]} output-model]
      {:status 200
       :body {:blob blob}})
    error))

(defn ->download-http [[output-model error]]
  (if (nil? error)
    (let [{:keys [image-stream]} output-model]
      {:status 200
       :headers {"Content-Type" "image/png"}
       :body image-stream})
    error))

;; usecase
(s/fdef upload
  :args (s/cat :input-model ::pics-domain/upload-input :image-db ::pics-service/pics-repository)
  :ret (s/or :success [::pics-domain/upload-output nil?]
             :failure [nil? any?]))

(s/fdef download
  :args (s/cat :input-model ::pics-domain/download-input :image-db ::pics-service/pics-repository)
  :ret (s/or :success [::pics-domain/download-output nil?]
             :failure [nil? any?]))

(defn upload [image-db input-model]
  (println image-db)
  (let [{:keys [image-buffer blob]} input-model
        [blob err] (border-error {:function #(pics-service/insert-pic image-db image-buffer blob)
                                  :error-wrapper (fn [message] {:status 500 :body {:message message}})})]
    (cond
      err [nil err]
      :else [{:blob blob} nil])))

(defn download [image-db input-model]
  (let [{:keys [blob]} input-model
        [image-stream err] (border-error {:function #(pics-service/get-pic image-db blob)
                                          :error-wrapper (fn [message] {:status 500 :body {:message message}})})]
    (cond
      err err
      (nil? image-stream) [nil {:status 500 :body {:message "image is not found"}}]
      :else [{:image-stream image-stream} nil])))

;; infrastructure
;; router
(defn app [image-db]
  (ring/ring-handler
   (ring/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "google cloud storage communicator"
                              :description ""}}
             :handler (swagger/create-swagger-handler)}}]
     ["/images"
      {:swagger {:tags ["images"]}}
      ["/upload"
       {:post {:summary "upload an image file"
               :parameters {:multipart {:file multipart/temp-file-part}}
               :responses {200 {:body {:blob string?}}}
               :handler (fn [input-data]
                          (->upload-http
                           (err->> input-data
                                   upload-http->
                                   (partial upload image-db))))}}]

      ["/download"
       {:get {:summary "download an image file"
              :swagger {:produces ["image/png"]}
              :parameters {:query {:filename string?}}
              :handler (fn [input-data]
                         (->download-http
                          (err->> input-data
                                  download-http->
                                  (partial download image-db))))}}]]]

    {:exception pretty/exception
     :data {:coercion reitit.coercion.spec/coercion
            :muuntaja m/instance
            :middleware [;; swagger
                         swagger/swagger-feature
                         ;; query-params & form-params
                         parameters/parameters-middleware
                           ;; content-negotiation
                         muuntaja/format-negotiate-middleware
                           ;; encoding response body
                         muuntaja/format-response-middleware
                           ;; exception handling
                         exception/exception-middleware
                           ;; decoding request body
                         muuntaja/format-request-middleware
                           ;; coercing response bodys
                         coercion/coerce-response-middleware
                           ;; coercing request parameters
                         coercion/coerce-request-middleware
                           ;; multipart
                         multipart/multipart-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))

;; server
(def server (atom nil))

(defn start [port image-db]
  (reset! server (jetty/run-jetty (app image-db) {:port port :join? false}))
  (println (format "server running in port %s" port)))

(defn stop []
  (when @server
    (.stop @server)))

;; cmd
(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 3030
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-b" "--bucket-name BUCKET_NAME" "[Required] Bucket name of GCS"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["This is the sample program to communicate Google Cloud Storage"
        ""
        "Usage: program-name [options]"
        ""
        "Options:"
        options-summary]
       (clojure.string/join \newline)))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options) {:exit-message (usage summary)}
      errors {:exit-messge (str errors)}
      (nil? (:bucket-name options)) {:exit-message (usage summary)}
      :else {:params options})))

(defn -main
  [& args]
  (let [{:keys [params exit-message]} (validate-args args)]
    (cond
      exit-message (println exit-message)
      :else (let [{:keys [bucket-name port]} params
                  [image-db err] (gcs-image-db-infrastructure/init bucket-name)]
              (if-not err
                (start port image-db)
                (println err))))))
