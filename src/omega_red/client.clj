(ns omega-red.client
  "Represents a Redis client that can be used to execute commands."
  (:require
   [omega-red.client.connection-pool :as client.connection-pool]
   [omega-red.redis]
   [omega-red.redis.command :as redis.command]
   [omega-red.redis.protocol :as redis.proto])
  (:import
   [java.net URI]
   [redis.clients.jedis JedisPoolConfig JedisPooled]
   [redis.clients.jedis.util JedisURIHelper]))

(defn create-pooled-connection
  "Creates pooled connection
  - `:uri` - the URI string of the Redis server, required
  - `:connection-pool` - optional, a map of connection pool settings or instance of `JedisPoolConfig`. Options:
    - `:max-total` - max total connections, defaults to 100
    - `:max-idle` - min total connections, defaults `max-total / 2`
    - `:min-idle` - min idle connections, defaults to 0 or `max-total / 10`
    - `:max-wait-millis` - max wait time for a connection, defaults to -1 (block until available)"
  [{:keys [uri connection-pool]}]
  (let [jedis-uri (URI. ^String uri)
        _ (when-not (JedisURIHelper/isValid jedis-uri)
            (throw (ex-info "invalid connection uri" {:uri uri})))
        pool-config (client.connection-pool/configure (or connection-pool {:max-total 100}))]
    (JedisPooled. ^JedisPoolConfig pool-config ^String uri)))

(defn create
  "Creates a Redis connection component.
  Args:
  - `:uri` - the URI string of the Redis server, required
  - `:connection-pool` - optional, a map of connection pool settings or instance of `JedisPoolConfig`
     See `create-pooled-connection` for more details.

  - `:key-prefix` - optional, a prefix for all keys, usually a service name - can be a string or keyword
  - `:ping-on-start?` - optional, when true will send a PING command to the server on start to check if it's alive
  "
  ;; TODO: add support for client-name param
  [{:keys [uri _client-name key-prefix ping-on-start? connection-pool]
    :or {ping-on-start? false}
    :as opts}]
  {:pre [uri
         (or (string? key-prefix)
             (keyword? key-prefix)
             (nil? key-prefix))]}
  (with-meta (assoc opts :connected? false)
    {'com.stuartsierra.component/start (fn start' [this]
                                         (if (:pool this)
                                           this
                                           (let [pool (create-pooled-connection {:uri uri
                                                                                 :connection-pool connection-pool})
                                                 _ (when-not pool
                                                     (throw (ex-info "something went wrong" {:pool pool})))
                                                 connected? (if ping-on-start?
                                                              (= "PONG" (redis.proto/execute* pool [:ping]))
                                                              ::unkown)]
                                             (-> this
                                                 (dissoc :uri)
                                                 (assoc :pool pool
                                                        :connected? connected?
                                                        :instance-addr (URI/.getAuthority (URI. ^String uri)))))))
     'com.stuartsierra.component/stop (fn stop' [this]
                                        (if-let [pool (:pool this)]
                                          (do
                                            (JedisPooled/.close pool)
                                            (assoc this :pool nil :connected? false))
                                          this))

     'omega-red.redis/execute (fn execute' [this cmd+args]
                                (redis.proto/execute* (:pool this)
                                                      (redis.command/process this cmd+args)))

     'omega-red.redis/execute-pipeline (fn execute-pipeline' [this cmds+args]
                                         (redis.proto/execute-pipeline* (:pool this)
                                                                        (mapv #(redis.command/process this %)
                                                                              cmds+args)))

     'omega-red.redis/transact (fn transact' [this cmds+args]
                                 (redis.proto/transact* (:pool this)
                                                        (mapv #(redis.command/process this %)
                                                              cmds+args)))}))
