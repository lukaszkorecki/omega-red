(ns omega-red.redis
  "Protocol for redis dispatch, and utilities for working with Redis, caching being the core use case"
  (:refer-clojure :exclude [key])
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [taoensso.carmine :as carmine]))

(defprotocol IRedis
  (execute
    [this cmd+args]
    "Executes single redis command - passed as JDBC-style vector: [:command the rest of args]")
  (execute-pipeline
    [this cmds+args]
    "Executes a series of commands + their args in a pipeline. Commands are a vector of vecs with the commands and their args. Use omega-red.protocol/excute-pipeline to invoke!"))

;; Command execution

(defn execute!
  "Executes a single Redis command as vector of command and arguments.:
  (execute! conn [:ping])
  (execute! conn [:set \"foo\" \"bar\"])"
  [conn cmd+args]
  {:pre [(seq cmd+args)]}
  (carmine/wcar conn
                (carmine/redis-call cmd+args)))

(defn execute-pipeline!
  "Executes a pipeline of Redis commands as a sequence of vectors of commands and arguments:

  (execute-pipeline! conn [[:ping]
                           [:set \"foo\" \"bar\"]
                           [:get \"foo\"]
                           [:del \"foo\"]])
  "
  [conn cmds+args]
  {:pre [(seq cmds+args)
         (every? seq cmds+args)]}
  (carmine/wcar conn
                :as-pipeline
                (apply carmine/redis-call cmds+args)))

;; alias key to taoensso.carmine/key, but with docstring
(def ^{:doc (:doc (meta (var taoensso.carmine/key)))}
  build-key
  taoensso.carmine/key)

;; See 'script' in dev-resources/omega_red/gen_cmd_config.clj for the code which generates this
;; The config is a map of:
;; { <cmd-kw> {:non-key-args-tail-count <int> :type <multi|single> :summary <str> :all-args <vec-of-str>}}
;; It basically tells us how many keys are in given command vector (one/many) and how many non-key args are at the end
;; so that it can be used to apply key prefixes, using 'key' function
(def redis-cmd-config
  (-> "redis-cmd-key-config.edn"
      io/resource
      slurp
      edn/read-string))

(defn apply-key-prefixes
  "Applies key prefix to the command and its arguments.
  It detects if given command accepts a key or a variadic number of keys
  and applies prefixes to them.
  NOTE: in most cases, the key is the first argument, followed by
  multiple keys with no extra arguments, and finally in very few cases
  a list of keys + optional arg is accepted. This fn applies prefixes to the keys only.
  "
  [{:keys [key-prefix]} cmd+args]
  (if-let [cmd-key-conf (and key-prefix
                             (get redis-cmd-config (first cmd+args)))]
    (let [{:keys [non-key-args-tail-count type]} cmd-key-conf]
      (cond
        ;; easy - 1st arg is key
        (= :single type) (update-in cmd+args [1] #(build-key key-prefix %))

        ;; a bit more complex - all args are keys
        (and (= :multi type) (zero? non-key-args-tail-count))
        (vec (concat [(first cmd+args)] (map #(build-key key-prefix %) (rest cmd+args))))

        ;; worst case scenario
        (and (= :multi type) (pos? non-key-args-tail-count))
        (let [[cmd & args] cmd+args]
          (vec
           (concat [cmd]
                   (map #(build-key key-prefix %) (drop-last non-key-args-tail-count args))
                   (drop (- (count args) non-key-args-tail-count) args))))
        :else
        (throw (ex-info "not sure how to deal with this" {:cmd+args cmd+args}))))
    ;; no key to deal with
    cmd+args))

;; Utilities & helpers

(defn cache-get-or-fetch
  "Tiny helper for the usual 'fetch from cache, and if there's a miss, use fetch function to get the data but also cache it'
  Note that, only truthy values are considered cache hits!
  Args:
  - `conn` - connection to the cache - instance of `Redis`
  - `options`
    - `cache-get` - function to fetch from Redis, accepts the connection as its first arg
    - `fetch` - the function to fetch data from a slow resource
    - `cache-set` - the function to store data in cache, args are:
      - `conn` - connection to the cache
      - `data` - fetched data
  Note:
  You need to ensure that resuls of `fetch` and `cache-get` return the same types, e.g. Redis' `SET foo 1`
  will cast 1 as string on read!"
  [conn {:keys [fetch cache-set cache-get]}]
  {:pre [(satisfies? IRedis conn)
         (fn? fetch)
         (fn? cache-set)
         (fn? cache-get)]}
  (if-let [from-cache (cache-get conn)]
    from-cache
    (let [fetch-res (fetch)]
      (cache-set conn fetch-res)
      fetch-res)))

(defn memoize-with-expiry
  "Similat to `clojure.core/memoize` but for Redis. The signature differs since we need a `key` for lookup.
  Assumes that cached value can be `SETEX` and `GET`-ed.
  Args:
  - `conn` - connection to Redis
  - `f` - the function to memoize
  Opt-map:
  - `key` - the key to use for lookup
  - `expiry-s` - expiry in seconds
  "
  [conn f {:keys [key expiry-s]}]
  {:pre [(fn? f)
         (and (number? expiry-s) (pos? expiry-s))
         (not (str/blank? key))]}
  (cache-get-or-fetch conn {:fetch f
                            :cache-get (fn cache-get' [conn]
                                         (execute conn [:get key]))
                            :cache-set (fn cache-set' [conn val]
                                         (execute conn [:setex key expiry-s val]))}))

(defn redis-client?
  "Can we use `thing` as a redis client?"
  [thing]
  (satisfies? IRedis thing))
