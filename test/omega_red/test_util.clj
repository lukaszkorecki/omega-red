(ns omega-red.test-util
  (:require
   [clojure.tools.logging :as log]
   [omega-red.redis :as redis]
   [omega-red.client :as redis.client]
   [com.stuartsierra.component :as component]))

(def redis-config
  (let [host (or (System/getenv "REDIS_HOST") "127.0.0.1")
        port (Integer/parseInt (or (System/getenv "REDIS_PORT") "6379"))]
    {:uri (str "redis://" host ":" port) :instance-addr (str host ":" port)}))

(def sys (atom nil))

(defn conn []
  (:redis @sys))

(defn prefixed-conn []
  (:redis-prefixed @sys))

(defn cleanup-all-data [conn]
  (redis/execute conn [:flushall]))

(defn with-test-system [test & [{:keys [cleanup? extra-components]
                                 :or {cleanup? true}}]]
  (let [sys-map (merge {:redis (redis.client/create redis-config)
                        :redis-prefixed (redis.client/create (assoc redis-config :key-prefix "test-prefix"))}

                       extra-components)]
    (try
      (reset! sys (component/start (component/map->SystemMap sys-map)))
      (when cleanup?
        (cleanup-all-data (conn)))
      (test)
      (catch Throwable e
        (log/error e "Error in test setup"))
      (finally
        (when cleanup?
          (cleanup-all-data (conn)))
        (component/stop @sys)))))


(defn make-timer []
  (let [start (System/currentTimeMillis)]
    (fn []
      (- (System/currentTimeMillis) ^long start))))
