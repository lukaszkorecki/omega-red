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
  (execute* conn [:ping])
  (execute* conn [:set \"foo\" \"bar\"])"
  [conn cmd+args]
  {:pre [(seq cmd+args)]}
  (carmine/wcar conn
                (carmine/redis-call cmd+args)))

(defn execute-pipeline!
  "Executes a pipeline of Redis commands as a sequence of vectors of commands and arguments:

  (execute-pipeline* conn [[:ping]
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
  key
  taoensso.carmine/key)

(def redis-cmds (-> "carmine-commands.edn"
                    io/resource
                    slurp
                    edn/read-string
                    (update-keys #(-> % str/lower-case keyword))))

;; TODO: use `redis-cmds` to:
;; - create a map of command->key-transformer-type - either single or variadic
;; - a function to detect key transformation based on the command
;; - a function to transform keys based on the command

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
