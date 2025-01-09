(ns omega-red.redis
  "Represents a redis connection spec,
  so we can use it when using Carmine, unlike the
  regular `wcar*` macro, recommended in Carmine docs."
  (:require
   [com.stuartsierra.component :as component]
   [omega-red.protocol :as proto]
   [taoensso.carmine.connections :as carmine.connections]
   [taoensso.carmine :as carmine]))

(defn get-redis-fn*
  "Finds actual function instance, based on a keyword.
  So:
  :hset -> taoensso.carmine/hset
  which then can be used as normal function"
  [fn-name-keyword]
  (ns-resolve 'taoensso.carmine (symbol fn-name-keyword)))

(def get-redis-fn (memoize get-redis-fn*))

(defn execute*
  [conn redis-fn+args]
  {:pre [(seq redis-fn+args)]}
  (let [[redis-fn & args] redis-fn+args]
    (carmine/wcar conn (apply (get-redis-fn redis-fn) args))))

(defn execute-pipeline*
  [conn redis-fns+args]
  {:pre [(seq redis-fns+args)]}
  (carmine/wcar conn
                :as-pipeline
                (mapv (fn [cmd+args]
                        (let [redis-fn (get-redis-fn (first cmd+args))]
                          (apply redis-fn (rest cmd+args))))
                      redis-fns+args)))

(defrecord Redis
  [spec conn-pool]
  component/Lifecycle
  (start
    [this]
    ;; XXX: bypass using the 'squirreled away' conn pool and create our own instance
    ;; https://github.com/ptaoussanis/carmine/issues/224
    (if (:conn-pool this)
      this
      (let [conn-pool (carmine/connection-pool {:test-on-borrow? true})]
        (assoc this :conn-pool conn-pool))))
  (stop
    [this]
    (if-let [conn-pool (:conn-pool this)]
      (do
        (java.io.Closeable/.close conn-pool)
        (assoc this :conn-pool nil))
      this))
  proto/Redis
  (execute
    [_ redis-fn+args]
    (execute* {:pool conn-pool} redis-fn+args))
  (execute-pipeline
    [_ redis-fns+args]
    (execute-pipeline* {:pool conn-pool} redis-fns+args)))

(defn create
  [{:keys [host port]}]
  {:pre [(string? host)
         (number? port)]}
  (map->Redis {:spec {:host host :port port}}))
