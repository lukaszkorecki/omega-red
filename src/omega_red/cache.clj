(ns omega-red.cache
  (:refer-clojure :exclude [memoize key])
  (:require
   [clojure.string :as str]
   [omega-red.redis :as redis]))

;; Utilities & helpers

(defn get-or-fetch
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
  {:pre [(redis/redis-client? conn)
         (fn? fetch)
         (fn? cache-set)
         (fn? cache-get)]}
  (if-let [from-cache (cache-get conn)]
    from-cache
    (let [fetch-res (fetch)]
      (cache-set conn fetch-res)
      fetch-res)))

(defn memoize
  "Similar to `clojure.core/memoize` but for Redis. The signature differs since we need a `key` for lookup.
  Assumes that cached value can be `SET` and `GET`-ed.
  NOTE: This is a simple memoization strategy and does not handle expiry! Assumes that something else will handle
  cache update or expiry. To enable expiry, pass `expiry-s` in the options map
  Args:
  - `conn` - connection to Redis
  Opt-map:
  - `fetch-fn` - the function to fetch
  - `key` - the key to use for lookup
  - `expiry-s` - expiry in seconds, optional
  "
  [conn {:keys [key fetch-fn expiry-s]}]
  {:pre [(fn? fetch-fn)
         (not (str/blank? key))]}
  (get-or-fetch conn {:fetch fetch-fn
                      :cache-get (fn cache-get' [conn]
                                   (redis/execute conn [:get key]))
                      :cache-set (fn cache-set' [conn val]
                                   (redis/execute conn
                                                  (if (number? expiry-s)
                                                    [:set key val "EX" expiry-s]
                                                    [:set key val])))}))
