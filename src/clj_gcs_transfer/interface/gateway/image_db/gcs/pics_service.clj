(ns clj-gcs-transfer.interface.gateway.image-db.gcs.pics-service
  "The implementation of pics-service as GCS"
  (:require [clj-gcs-transfer.interface.gateway.image-db.pics-service :refer [Pics]]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as timbre])
  (:import [com.google.cloud.storage Blob Blob$BlobSourceOption BlobId BlobInfo Storage$BlobListOption Storage$BlobWriteOption]
           com.google.common.io.ByteStreams
           java.awt.image.BufferedImage
           java.io.FileInputStream
           java.nio.channels.Channels
           javax.imageio.ImageIO))

(s/def :temp-file/prefix (s/and string? #(< 3 (count %))))
(s/def :temp-file/suffix (s/and string?))
(s/def ::temp-file-config (s/keys :req-un [:temp-file/prefix :temp-file/suffix]))

(defn- blob->input-stream
  ([^Blob blob blob-source-options]
   (Channels/newInputStream
    (.reader blob blob-source-options)))
  ([^Blob blob]
   (timbre/info "load image request:" (.getName blob) (.getSelfLink blob))
   (blob->input-stream blob (make-array Blob$BlobSourceOption 0))))

(extend-protocol Pics
  clj_gcs_transfer.infrastructure.gcs_image_db.core.GCSImageDBBoundary
  (get-pics [{:keys [storage bucket-name]}]
    (let [blob-list-options (into-array [(Storage$BlobListOption/pageSize 20)])]
      (map blob->input-stream
           (-> (.list storage bucket-name blob-list-options)
               .iterateAll
               .iterator
               iterator-seq))))

  (insert-pic [{:keys [storage bucket-name]} ^BufferedImage image-buffer blob]
    (let [temp-file (java.io.File/createTempFile "works" ".png")
          storage-blob-write-options (make-array Storage$BlobWriteOption 0)
          blob-id (BlobId/of bucket-name blob)
          blob-info (-> (BlobInfo/newBuilder blob-id) (.setContentType "image/png")  .build)]
      (when (and temp-file (ImageIO/write image-buffer "png" temp-file))
        (let [status (with-open [from (-> temp-file FileInputStream. .getChannel)
                                 to (.writer storage blob-info storage-blob-write-options)]
                       (ByteStreams/copy from to))]
          (.delete temp-file)
          (timbre/info (format "save the image %s: status %s" blob status))
          (if status blob (throw (ex-info (format "save the image %s into gcs failed" blob) {})))))))

  (get-pic [{:keys [storage bucket-name]} blob]
    (let [gcs-blob (.get storage (BlobId/of bucket-name blob))
          blob-source-option (make-array Blob$BlobSourceOption 0)]
      (if (and gcs-blob (.exists gcs-blob blob-source-option))
        (blob->input-stream gcs-blob blob-source-option)
        nil)))

  (delete-pic [{:keys [storage bucket-name]} blob]
    (let [gcs-blob (.get storage (BlobId/of bucket-name blob))
          blob-source-option (make-array Blob$BlobSourceOption 0)]
      (when (and gcs-blob (.exists gcs-blob blob-source-option))
        (.delete gcs-blob blob-source-option)))))

;; --- sample usage ---
;; (def db
;;   (clj-gcs-transfer.infrastructure.gcs-image-db.core/->GCSImageDBBoundary
;;    (clj-gcs-transfer.infrastructure.gcs-image-db.core/get-storage)
;;    "portcard-captures"))

;; (clj-gcs-transfer.interface.gateway.image-db.pics-service/get-pics db)

;; (clj-gcs-transfer.interface.gateway.image-db.pics-service/insert-pic
;;  db
;;  (ImageIO/read (io/file (io/resource "sample.png")))
;;  "sample2.png")

;; (clj-gcs-transfer.interface.gateway.image-db.pics-service/get-pic
;;  db "sample2.png")

;; (clj-gcs-transfer.interface.gateway.image-db.pics-service/delete-pic
;;  db "sample2.png")

;; (clj-gcs-transfer.interface.gateway.image-db.pics-service/get-pic
;;  db "sample3-.png")
;; -----------------------

;; --- develop docs ---
;; (.downloadTo
;;  (first (clj-gcs-transfer.interface.gateway.image-db.pics-service/get-pics db))
;;  (java.nio.file.Paths/get "sample-copy.png" (make-array String 0)))

;; (java.io.ByteArrayInputStream.
;;  (.getContent
;;   (first (clj-gcs-transfer.interface.gateway.image-db.pics-service/get-pics db))
;;   (make-array Blob$BlobSourceOption 0)))

;; (defn- create-temp-file! [{:keys [temp-file-config]}]
;;   {:pre [(s/valid? ::temp-file-config temp-file-config)]}
;;   (let [{:keys [prefix suffix]} temp-file-config]
;;     (try [(java.io.File/createTempFile prefix suffix) nil]
;;          (catch Exception e
;;            (timbre/error "cannot create temp-file" (.getMessage e))
;;            [nil {:status 500 :body {:code 10001 :message "cannot create temp-file"}}]))))
