(ns clj-gcs-transfer.interface.gateway.image-db.pics-service
  "The interface of Image Accessor"
  (:import [java.awt.image BufferedImage])
  (:require [clojure.spec.alpha :as s]
            [clj-gcs-transfer.domain.pics :as pics-domain]))

(defprotocol Pics
  (get-pics [db])
  (insert-pic [db ^BufferedImage image-buffer blob])
  (get-pic [db blob])
  (delete-pic [db blob]))

(defn- pics-repository? [inst]
  (satisfies? Pics inst))

(s/def ::pics-repository pics-repository?)

(s/fdef get-pics
  :args (s/cat :db ::pics-repository)
  :ret (s/coll-of ::pics-domain/image-stream))

(s/fdef insert-pic
  :args (s/cat :db ::pics-repository
               :image-buffer ::pics-domain/image-buffer
               :blob ::pics-domain/blob)
  :ret ::pics-domain/blob)

(s/fdef get-pic
  :args (s/cat :db ::pics-repository
               :blob ::pics-domain/blob)
  :ret (s/or :exist ::pics-domain/image-stream
             :not-exist empty?))

(s/fdef delete-pic
  :args (s/cat :db ::pics-repository
               :blob ::pics-domain/blob)
  :ret boolean?)
