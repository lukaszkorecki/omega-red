(ns omega-red.client.connection-pool
  "Utility namespace for streamlining connection pool creation & configuration"

  (:import
   [redis.clients.jedis JedisPoolConfig]))

;; NOTE: this is very primitive, use something better
(defn validate-config
  [{:keys [max-total max-idle min-idle max-wait-millis] :as config}]
  (and (nat-int? max-total)
       (nat-int? max-idle)
       (nat-int? min-idle)
       (nat-int? max-wait-millis)
       (<= min-idle max-idle max-total)
       config))
;; see here https://www.site24x7.com/blog/jedis-pool-optimization
(defn configure
  ^JedisPoolConfig
  [{:keys [max-total max-idle min-idle max-wait-millis] :as config}]
  (when-not (validate-config config)
    (throw (ex-info "invalid connection pool settings" config)))
  (let [pool-config (JedisPoolConfig.)]
    (doto pool-config
      (.setMaxTotal max-total)
      (.setMaxIdle max-idle)
      (.setMinIdle min-idle)
      (.setBlockWhenExhausted true)
      (.setMaxWaitMillis max-wait-millis)
      (.setTestOnBorrow false)
      (.setMinEvictableIdleTimeMillis 60000)
      (.setJmxEnabled false)
      (.setTestOnReturn false)
      (.setTestWhileIdle true))

    pool-config))
