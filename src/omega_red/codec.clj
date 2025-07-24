(ns omega-red.codec
  (:require
   [clojure.string :as str]
   [cognitect.transit :as transit])
  (:import
   [java.io ByteArrayInputStream ByteArrayOutputStream]))

(set! *warn-on-reflection* true)

;; NOTE: to disambiguate regular strings from serialized Clojure data
;;       Clojure serialization will add a known prefix
;;       To make sure we can deal with changes to how we serialize data - we will encode
;;       serialization version using the prefix
(def ^:private ser-prefix "~cV1~")
(def ^:private ser-prefix-len (count ser-prefix))

(defn- serialize-clj [input]
  (with-open [out (ByteArrayOutputStream. 64)] ;; short buffer to reduce allocations
    (let [writer (transit/writer out :json)]
      (transit/write writer input)
      (str ser-prefix (String. (ByteArrayOutputStream/.toByteArray out) "UTF-8")))))

(defn- unserialize-clj [data-bag]
  (let [data-bag-no-prefix (subs data-bag ser-prefix-len)
        bytes (String/.getBytes data-bag-no-prefix "UTF-8")]
    (with-open [in (ByteArrayInputStream. bytes)]
      (transit/read (transit/reader in :json)))))

(defn- get-string-or-unserialize-clj-data [data-bag]
  (let [str (String. ^bytes data-bag "UTF-8")]
    (if (str/starts-with? str ser-prefix)
      (unserialize-clj str)
      str)))

(defn serialize [thing]
  (cond
   (boolean? thing) (str thing)
   (number? thing) (str thing)
   (string? thing) thing
    ;; XXX: things will blow up if we start passing random Java classes, maybe that's for the best?
   :else (serialize-clj thing)))

(defn deserialize [res]
  (cond
   (number? res) res
   (bytes? res) (get-string-or-unserialize-clj-data res)
    ;; XXX: should we protect against recursion here?
    ;; ArrayList is used for pipeline results, Redis collection types (sets, hash maps etc)
   (instance? java.util.ArrayList res) (mapv deserialize res)
   :else res))

(defn prefixable? [i]
  (or (string? i) (keyword? i)))

(defn serialize-key
  "Simplifies dealing with composite keys - rather than stitching them by hand
  using `str`, it supports transparently converting a vec of strings/keywords into
  `:` delimited string.
  NOTE: `nil` values will be omitted, but the vec cannot be empty!
  "
  [args]
  {:pre [(every? #(or (string? %) (keyword? %) (nil? %)) args)
         (seq (remove nil? args))]}
  (->> args
       (remove nil?)
       (mapv (fn [segment]
               (if (qualified-keyword? segment)
                 (str (namespace segment) "/" (name segment))
                 (name segment))))
       (remove str/blank?)
       (str/join ":")))
