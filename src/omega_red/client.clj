;; XXX: rename to omega-red.connection? merge into omega-red.redis?

(ns omega-red.client
  "Represents a Redis client that can be used to execute commands."
  (:require
   [omega-red.redis]
   [omega-red.redis.protocol :as redis.proto])
  (:import
   [redis.clients.jedis JedisPooled]))

(defn create
  "Creates a Redis connection component.
  Args:
  - `:uri` - the URI string of the Redis server, required
  - `:key-prefix` - optional, a prefix for all keys, usually a service name - can be a string or keyword
  - `:ping-on-start?` - optional, when true will send a PING command to the server on start to check if it's alive
  "
  [{:keys [uri key-prefix ping-on-start?] :as opts}]
  {:pre [uri
         (or (string? key-prefix)
             (keyword? key-prefix)
             (nil? key-prefix))]}
  (with-meta opts
    {'com.stuartsierra.component/start (fn start' [this]
                                         (if (:pool this)
                                           this
                                           (let [;; FIXME: handle all other options? Or just use URI for now?
                                                 ;; TODO: add support for connection pool configuration - see:
                                                 ;; https://www.site24x7.com/blog/jedis-pool-optimization
                                                 pool (JedisPooled. ^String uri)]
                                             (assoc this :pool pool :connected? (if ping-on-start?
                                                                                  (= "PONG" (redis.proto/execute* pool [:ping]))
                                                                                  ::unkown)))))
     'com.stuartsierra.component/stop (fn stop' [this]
                                        (if-let [pool (:pool this)]
                                          (do
                                            (JedisPooled/.close pool)
                                            (assoc this :pool nil :connected? false))
                                          this))

     'omega-red.redis/execute (fn execute' [this cmd+args]
                                (redis.proto/execute* (:pool this)
                                                      (redis.proto/apply-key-prefixes this cmd+args)))
     'omega-red.redis/execute-pipeline (fn execute-pipeline' [this cmds+args]
                                         (redis.proto/execute-pipeline* (:pool this)
                                                                        (mapv #(redis.proto/apply-key-prefixes this %)
                                                                              cmds+args)))

     'omega-red.redis/inspect (fn inspect' [this]
                                (dissoc this :pool))}))
