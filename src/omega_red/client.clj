(ns omega-red.client
  "Represents a redis connection spec,
  so we can use it when using Carmine, unlike the
  regular `wcar*` macro, recommended in Carmine docs."
  (:require
   [com.stuartsierra.component :as component]
   [omega-red.redis :as redis]
   [taoensso.carmine :as carmine]))

(defrecord Redis
  [;; inputs
   spec
   options

   ;; derived state
   key-prefix
   pool]
  component/Lifecycle
  (start
    [this]
    ;; XXX: bypass using the 'squirreled away' conn pool and create our own instance
    ;; https://github.com/ptaoussanis/carmine/issues/224
    (if (:pool this)
      this
      (let [pool (carmine/connection-pool {:test-on-borrow? true})]
        (assoc this :pool pool :key-prefix (:key-prefix options)))))
  (stop
    [this]
    (if-let [pool (:pool this)]
      (do
        (java.io.Closeable/.close pool)
        (assoc this :pool nil))
      this))
  redis/IRedis
  (execute
    [this cmd+args]
    (redis/execute! this
                    (redis/apply-key-prefixes {:key-prefix key-prefix} cmd+args)))
  (execute-pipeline
    [this cmds+args]
    (redis/execute-pipeline! this
                             (mapv #(redis/apply-key-prefixes {:key-prefix key-prefix} %) cmds+args))))

;; Hmmm https://github.com/redis/redis-doc/blob/master/commands.json
;; there is a programatic way of figuring out which argument(s) are keys
;; and could be automatically prefixed

(defn create
  "Creates a Redis connection component.
  Args:
  - `conn-spec` - a map, same one as `:spec` key of map that `wcar` macro accepts
  - `options` - optional, a map of:
     - `:key-prefix` - a prefix for all keys, usually a service name
     - `:auto-prefix-commands` - a set with keywords as commands that should have the prefix added automatically to the key, defaults to all commands, takes effect only if `key-prefix` is set"
  ([conn-spec]
   (create conn-spec {}))
  ([conn-spec options]
   {:pre [(or (string? (:key-prefix options))
              (keyword? (:key-prefix options))
              (nil? (:key-prefix options)))]}
   (map->Redis {:spec conn-spec :options options})))
