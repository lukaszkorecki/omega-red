(ns omega-red.cache
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

(defn memoize-with-expiry
  "Similar to `clojure.core/memoize` but for Redis. The signature differs since we need a `key` for lookup.
  Assumes that cached value can be `SET+EX` and `GET`-ed.
  Args:
  - `conn` - connection to Redis
  - `f` - the function to memoize
  Opt-map:
  - `key` - the key to use for lookup
  - `expiry-s` - expiry in seconds
  "
  [conn fetch-fn {:keys [key expiry-s]}]
  {:pre [(fn? fetch-fn)
         (and (number? expiry-s) (pos? expiry-s))
         (not (str/blank? key))]}
  (get-or-fetch conn {:fetch fetch-fn
                      :cache-get (fn cache-get' [conn]
                                   (redis/execute conn [:get key]))
                      :cache-set (fn cache-set' [conn val]
                                   (redis/execute conn [:set key val "EX" expiry-s]))}))
