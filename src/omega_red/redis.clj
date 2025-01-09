(ns omega-red.redis)

(defprotocol IRedis
  (execute
    [this cmd+args]
    "Executes single redis command - passed as JDBC-style vector: [:command the rest of args]")
  (execute-pipeline
    [this cmds+args]
    "Executes a series of commands + their args in a pipeline. Commands are a vector of vecs with the commands and their args. Use omega-red.protocol/excute-pipeline to invoke!"))

(defn cache-get-or-fetch
  "Tiny helper for the usual 'fetch from cache, and if there's a miss, use fetch function to get the data but also cache it'
  Args:
  - `conn` - connection to the cache - instance of `Redis`
  - `options`
    - `cache-get` - function to fetch from cache (usally redis), accepts the connection
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
