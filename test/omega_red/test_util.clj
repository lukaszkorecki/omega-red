(ns omega-red.test-util
  (:require
   [omega-red.client :as redis.client]
   [com.stuartsierra.component :as component]))

(def redis-config
  {:host (or (System/getenv "REDIS_HOST") "127.0.0.1")
   :port (Integer/parseInt (or (System/getenv "REDIS_PORT") "6379"))})

(def sys (atom nil))

(defn conn []
  (:redis @sys))

(defn with-test-system [test]
  (try
    (reset! sys (component/start (component/map->SystemMap
                                  {:redis (redis.client/create redis-config)})))
    (test)

    (finally
      (component/stop @sys))))
