(ns omega-red.client.connection-pool
  "Utility namespace for streamlining connection pool creation & configuration"
  (:require [clojure.spec.alpha :as s])
  (:import
   [redis.clients.jedis JedisPoolConfig]))

(set! *warn-on-reflection* true)

(defn- zero-or-more-int [x]
  (and (int? x) (>= x 0)))

(s/def ::max-total nat-int?)
(s/def ::max-idle nat-int?)
(s/def ::min-idle zero-or-more-int)
(s/def ::max-wait-millis int?)
(s/def ::config (s/keys :req-un [::max-total]
                        :opt-un [::max-idle
                                 ::min-idle
                                 ::max-wait-millis]))

(defn validate-config
  [config]
  (when-not (s/valid? ::config config)
    (throw (ex-info "invalid pool configuration" {:config config
                                                  :problems (s/explain-data ::config config)})))
  config)

;; see here https://www.site24x7.com/blog/jedis-pool-optimization
(defn calculate-defaults [{:keys [max-total max-idle min-idle] :as config}]
  (cond-> (update-vals config long)
          (nil? max-idle) (assoc :max-idle (max 1 (int (/ max-total 2))))
          (nil? min-idle) (assoc :min-idle (max 1 (int (/ max-total 10))))))

(defn pool-config? [x]
  (instance? JedisPoolConfig x))

(defn configure
  ^JedisPoolConfig
  [config]
  (if (pool-config? config)
    config
    (let [{:keys [max-total max-idle min-idle max-wait-millis]} (-> config
                                                                    validate-config
                                                                    calculate-defaults
                                                                    validate-config)
          pool-config ^JedisPoolConfig (doto (JedisPoolConfig.)
                                         (.setMaxTotal max-total)
                                         (.setMaxIdle max-idle)
                                         (.setMinIdle min-idle)
                                         (.setBlockWhenExhausted true)
                                         (.setTestOnBorrow false)
                                         (.setMinEvictableIdleTimeMillis 60000)
                                         (.setJmxEnabled false)
                                         (.setTestOnReturn false)
                                         (.setTestWhileIdle true))]
      (when max-wait-millis
        (.setMaxWaitMillis pool-config max-wait-millis))
      pool-config)))
