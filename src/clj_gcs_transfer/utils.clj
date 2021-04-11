(ns clj-gcs-transfer.utils
  (:require [taoensso.timbre :as timbre]))

(defn bind-error
  "The helper function of the macro err->>"
  [f [val err]]
  (if (nil? err)
    (f val)
    [nil err]))

(defmacro err->>
  "The macro of error handling

  Examples:

  ```clojure
  (defn increment [i]
    (if (int? i) [(inc i) nil]  [nil \"invalid input\"]))

  (defn divide [i]
    (if (not (zero? i)) [(/ 100 i) nil] [nil \"cannot divide by zero\"]))

  (err->> \"hello\" increment divide)
  ;; [nil \"invalid input\"]

  (err->> 0 increment divide)
  ;; [0 nil]

  (err->> -1 increment divide)
  ;; [nil \"cannot divide by zero\"]
  ```
  "
  [val & fns]
  (let [fns (for [f fns] `(bind-error ~f))]
    `(->> [~val nil]
          ~@fns)))

(defn border-error
  "try-catch wrapper

  Example:

  ```clojure
  (defn divice [x y]
    (/ x y))

  (border-error {:function #(divide 1 0)
                 :error-wrapper (fn [message] {:status 500 :body {:message message}})})
  ```
  "

  [{:keys [function error-wrapper]}]
  (try (let [result (function)]
         [result nil])
       (catch clojure.lang.ExceptionInfo e
         (timbre/warn (.getMessage e))
         [nil (error-wrapper (str "spec exception: " (.getMessage e)))])
       (catch java.lang.AssertionError e
         (timbre/warn (.getMessage e))
         [nil (error-wrapper (str "spec exception: " (.getMessage e)))])
       (catch Exception e
         (timbre/warn e)
         [nil (error-wrapper (str "unknown exception: " (.getMessage e)))])))
