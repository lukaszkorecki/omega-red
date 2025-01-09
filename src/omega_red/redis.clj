(ns omega-red.redis
  "Represents a redis connection spec,
  so we can use it when using Carmine, unlike the
  regular `wcar*` macro, recommended in Carmine docs."
  (:require
   [com.stuartsierra.component :as component]
   [omega-red.protocol :as proto]
   [taoensso.carmine :as carmine]))

(defn execute*
  "Executes a single Redis command as vector of command and arguments.:
  (execute* conn [:ping])
  (execute* conn [:set \"foo\" \"bar\"])"
  [conn cmd+args]
  {:pre [(seq cmd+args)]}
  (carmine/wcar conn
                (carmine/redis-call cmd+args)))

(defn execute-pipeline*
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

(defrecord Redis
  [spec pool]
  component/Lifecycle
  (start
    [this]
    ;; XXX: bypass using the 'squirreled away' conn pool and create our own instance
    ;; https://github.com/ptaoussanis/carmine/issues/224
    (if (:pool this)
      this
      (let [pool (carmine/connection-pool {:test-on-borrow? true})]
        (assoc this :pool pool))))
  (stop
    [this]
    (if-let [pool (:pool this)]
      (do
        (java.io.Closeable/.close pool)
        (assoc this :pool nil))
      this))
  proto/Redis
  (execute
    [this cmd+args]
    (execute* this #_{:spec spec :pool pool} cmd+args))
  (execute-pipeline
    [this cmds+args]
    (execute-pipeline* this #_{:spec spec :pool pool} cmds+args)))

(defn create
  ;; TODO: add more of keys supported by `:spec`?
  [{:keys [host port]}]
  {:pre [(string? host)
         (number? port)]}
  (map->Redis {:spec {:host host :port port}}))
