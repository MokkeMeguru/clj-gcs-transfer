(ns clj-gcs-transfer.infrastructure.gcs-image-db.core
  (:require [clojure.spec.alpha :as s]
            [taoensso.timbre :as timbre])
  (:import [com.google.cloud.storage Bucket Storage Storage$BucketGetOption StorageException StorageOptions]))

;; data specs
(s/def ::storage-options (partial instance? StorageOptions))
(s/def ::storage (partial instance? Storage))
(s/def ::bucket-name string?)
(s/def ::bucket (partial instance? Bucket))

(s/def ::gcs-image-db-boundary (s/keys :req-un [::storage ::bucket-name]))

;; data types
(defrecord GCSImageDBBoundary [storage bucket-name])

;; constructor
(s/fdef ->GCSImageDBBoundary
  :args (s/cat :storage ::storage :bucket-name ::bucket-name)
  :ret ::gcs-image-db-boundary)


;; methods spec


(s/fdef get-storage
  :args (s/cat :storage-options (s/? ::storage-options))
  :ret ::storage)

(s/fdef get-bucket
  :args (s/cat :gcs-image-db-boundary ::gcs-image-db-boundary)
  :ret (s/or :exist ::bucket
             :not-exist empty?))

(s/fdef bucket-exist?
  :args (s/cat :gcs-image-db-boundary ::gcs-image-db-boundary)
  :ret boolean?)

(defn get-storage
  "get storage using the environment variable GOOGLE_APPLICATION_CREDENTIALS

  Parameters:

  - storage-options Please refer the docs of com.google.cloud.storage.StorageOptions

  Notice:

  Please run under the environment which run below command.

      export GOOGLE_APPLICATION_CREDENTIALS=\"<path-to-your-secret-json>\"

  References:

  - https://cloud.google.com/storage/docs/reference/libraries#setting_up_authentication
  "
  ([]
   (get-storage (StorageOptions/getDefaultInstance)))
  ([storage-options]
   {:pre [(instance? StorageOptions storage-options)]}
   (.getService storage-options)))

(defn get-bucket
  "get bucket of this boundary"
  [gcs-image-db-boundary]
  {:pre [(s/valid? ::gcs-image-db-boundary gcs-image-db-boundary)]
   :post [(or (instance? Bucket %) (nil? %))]}
  (try
    (let [{:keys [storage bucket-name]} gcs-image-db-boundary]
      (-> storage
          (.get bucket-name (make-array Storage$BucketGetOption 0))))
    (catch StorageException e
      (timbre/error (.getMessage e)))))

(defn- bucket-exist? [gcs-image-db-bounday]
  (some? (get-bucket gcs-image-db-bounday)))

;; initializer
(s/fdef init
  :args (s/cat :bucket-name ::bucket-name :storage (s/? ::storage))
  :ret (s/or :success (s/tuple ::gcs-image-db-boundary nil?)
             :failure (s/tuple nil? string?)))

(defn init
  "initizer of Google Cloud Storage Accessor

  Parameters:

  - bucket-name  GCS's bucket name
  - storage      GCS API's storage object

  Example:

      (init \"sample box\")
  
  "
  ([bucket-name]
   (init bucket-name (get-storage)))
  ([bucket-name storage]
   (let [gcs-image-db-boundary (->GCSImageDBBoundary storage bucket-name)]
     (if (bucket-exist? gcs-image-db-boundary)
       [gcs-image-db-boundary nil]
       [nil (format "cannot initialize gcs-image-db-boundary: the bucket \"%s\"cannot accessable" bucket-name)]))))

;; (init "portcard-captures")

;; (iterator-seq
;;  (.iterator
;;   (.iterateAll
;;    (.list storage
;;           (into-array [(Storage$BucketListOption/pageSize 100)])))))

;; (.get storage "portcard-captures"
;;       (make-array Storage$BucketGetOption 0)
;;       ;; (into-array [;; (Storage$BucketGetOption/userProject "portcard")
;;       ;;              ])
;;       )
;; (into-array [(Storage$BucketGetOption/userProject "temp")])
;; (type (make-array Storage$BucketGetOption 0))
