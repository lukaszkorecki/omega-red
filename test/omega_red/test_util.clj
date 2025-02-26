(ns omega-red.test-util
  (:require
   [omega-red.redis :as redis]
   [omega-red.client :as redis.client]
   [com.stuartsierra.component :as component]))

(def redis-config
  (let [host (or (System/getenv "REDIS_HOST") "127.0.0.1")
        port (Integer/parseInt (or (System/getenv "REDIS_PORT") "6379"))]
    {:uri (str "redis://" host ":" port)}))

(def sys (atom nil))

(defn conn []
  (:redis @sys))

(defn clean-up-all-data [conn]
  (when-let [all-keys (seq (redis/execute conn [:keys "*"]))]
    (redis/execute conn (concat [:del] all-keys))))

(defn with-test-system [test]
  (try
    (reset! sys (component/start (component/map->SystemMap
                                  {:redis (redis.client/create redis-config)
                                   :redis-prefixed (redis.client/create redis-config {:key-prefix "test-prefix"})})))

    (test)

    (finally
      (component/stop @sys))))
