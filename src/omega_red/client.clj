;; XXX: rename to omega-red.connection? merge into omega-red.redis?

(ns omega-red.client
  "Represents a Redis client that can be used to execute commands."
  (:require
   [com.stuartsierra.component :as component]
   [omega-red.redis :as redis]
   [omega-red.redis.protocol :as redis.proto])

  (:import
   [redis.clients.jedis JedisPooled]))

(defrecord Redis [;; inputs
                  uri
                  key-prefix

                  ;; derived state
                  pool]
  component/Lifecycle
  (start
    [this]
    (if (:pool this)
      this
      (let [;; FIXME: handle all other options? Or just use URI for now?
            ;; TODO: add support for connection pool configuration - see:
            ;; https://www.site24x7.com/blog/jedis-pool-optimization
            pool (JedisPooled. ^String uri)]
        (assoc this :pool pool))))
  (stop
    [this]
    (if-let [pool (:pool this)]
      (do
        (JedisPooled/.close pool)
        (assoc this :pool nil))
      this))

  redis/IRedis
  (execute
    [this cmd+args]
    (redis.proto/execute* (:pool this)
                          (redis.proto/apply-key-prefixes {:key-prefix key-prefix} cmd+args)))
  (execute-pipeline
    [this cmds+args]
    (redis.proto/execute-pipeline* (:pool this)
                                   (mapv #(redis.proto/apply-key-prefixes {:key-prefix key-prefix} %)
                                         cmds+args))))

(defn create
  "Creates a Redis connection component.
  Args:
  - `:uri` - the URI string of the Redis server, required
  - `:key-prefix` - optional, a prefix for all keys, usually a service name - can be a string or keyword"
  [{:keys [uri key-prefix] :as opts}]
  {:pre [uri
         (or (string? key-prefix)
             (keyword? key-prefix)
             (nil? key-prefix))]}
  (map->Redis opts))
