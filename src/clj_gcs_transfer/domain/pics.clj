(ns clj-gcs-transfer.domain.pics
  (:require [clojure.spec.alpha :as s])
  (:import [java.awt.image BufferedImage]))

(s/def ::image-buffer (partial instance? BufferedImage))
(s/def ::image-stream (partial instance? java.io.InputStream))
(s/def ::blob string?)

(s/def ::upload-input
  (s/keys :req-un [::image-buffer ::blob]))

(s/def ::upload-output
  (s/keys :req-un [::blob]))

(s/def ::download-input
  (s/keys :req-un [::blob]))

(s/def ::download-output
  (s/keys :req-un [::image-stream]))
