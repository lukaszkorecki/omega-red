(ns omega-red.redis.command
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [omega-red.codec :as codec]))

;; run in repl to generate this config
(comment
  (require '[omega-red.gen-cmd-config :as gen-cmd-config] :reload)
  (gen-cmd-config/-main))

;; Commands are generated ^^^^^ schema is:
;; {:cmd.name {:single-key-arg? bool
;;             :key-index <int> - index of a key in the command's args, starting from 1 (0th is the command)
;;             ; only present if single-key-arg is false
;;             :key-specs { }
(def cmd-config
  (-> "redis-commands.edn"
      io/resource
      slurp
      edn/read-string))

(defn- first-key-prefixer [cmd+args prefixer]
  (update-in cmd+args [1] prefixer))

(defn- block-key-prefixer [cmd+args prefixer]
  (->> cmd+args
       (map-indexed (fn [idx i]
                      (if (odd? idx)
                        (prefixer i)
                        i)))))

(defn- first-key-variadic-prefixer
  "Prefixes all keys in the command with the prefixer. e.g `[:del \"foo\" \"bar\"]` -> `[:del \"prefix:foo\" \"prefix:bar\"]`"
  [[cmd & args] prefixer]
  (vec (concat [cmd] (mapv prefixer args))))

(defn no-op-prefixer [cmd+args _prefixer]
  cmd+args)

(def key-processors
  (->> cmd-config
       (filter (fn [[_cmd-kw {:keys [process-keys?]}]]
                 process-keys?))
       (map (fn [[cmd-kw {:keys [is-key-first-arg?
                                 has-only-one-key-arg?
                                 has-variadic-key-args?
                                 has-block-key-args?] :as _spec}]]
              (let [prefixer-fn (cond
                                  has-only-one-key-arg? first-key-prefixer
                                  (and is-key-first-arg? has-block-key-args?) block-key-prefixer
                                  (and is-key-first-arg? has-variadic-key-args?) first-key-variadic-prefixer
                                  :else no-op-prefixer)]
                (hash-map cmd-kw prefixer-fn))))
       (into {})))

(def token-processors
  (->> cmd-config
       (filter (fn [[_cmd-kw {:keys [process-tokens?]}]]
                 process-tokens?))
       (map (fn [[cmd-kw {:keys [tokens]}]]
              ;; generate translation of lower & upper case strings & keywords to upper case strings
              (let [token-variants (->> tokens
                                        (reduce (fn [acc token] ;; always starts uppercase
                                                  (assoc acc
                                                         (keyword (str token)) token
                                                         (keyword (str/lower-case (str token))) token))
                                                {}))
                    converter-fn (fn process-tokens' [[cmd & args]]
                                   (vec (concat [cmd] (mapv (fn [arg]
                                                              (get token-variants arg arg))
                                                            args))))]

                (hash-map cmd-kw converter-fn))))
       (into {})))

(defn- make-key-formatter [key-prefix]
  (fn with-prefix'
    [a-key]
    (if (codec/prefixable? a-key)
      (codec/serialize-key [key-prefix a-key])
      a-key)))

(defn process [{:keys [key-prefix]} cmd+args]
  (let [cmd+args (if-let [key-processor (get key-processors (first cmd+args))]
                   (let [key-formatter (make-key-formatter key-prefix)]
                     (key-processor cmd+args key-formatter))
                   ;; no command config, skip
                   ;; XXX: add debug log?
                   cmd+args)]
    (if-let [token-processor (get token-processors (first cmd+args))]
      (token-processor cmd+args)
      cmd+args)))
