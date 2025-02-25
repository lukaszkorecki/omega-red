(ns omega-red.codec
  (:require
   [clojure.string :as str]
   [clojure.data.fressian :as fress]))

(defn serialize-clj [thing]
  (fress/write thing))

(defn unserialize-clj [thing]
  (fress/read thing))

(defn serialize-args [args]
  (mapv str args))

(defn deserialize-result [res]
  (cond
    (number? res) res
    (bytes? res) (String. ^bytes res "UTF-8")
    ;; XXX: should we protect against recursion here?
    (instance? java.util.ArrayList res) (mapv deserialize-result res)
    :else res))

(defn serialize-key [& args]
  {:pre [(every? #(or (string? %) (keyword? %) (nil? %)) args)]}
  (->> args
       (remove nil?)
       (mapv (fn [segment]
               (if (qualified-keyword? segment)
                 (str (namespace segment) "/" (name segment))
                 (name segment))))
       (str/join ":")))
