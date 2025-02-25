(ns omega-red.redis
  "Protocol for redis dispatch, and utilities for working with Redis, caching being the core use case"
  (:refer-clojure :exclude [key])
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [taoensso.carmine :as carmine])

  (:import
   [redis.clients.jedis Jedis UnifiedJedis ;; TODO <--- use this instead of JedisPooled or AbstractPipeline when dispatching
    Response
    AbstractPipeline JedisPooled Protocol$Command]))

(set! *warn-on-reflection* true)

(defprotocol IRedis
  (execute
    [this cmd+args]
    "Executes single redis command - passed as JDBC-style vector: [:command the rest of args]")
  (execute-pipeline
    [this cmds+args]
    "Executes a series of commands + their args in a pipeline. Commands are a vector of vecs with the commands and their args. Use omega-red.protocol/excute-pipeline to invoke!"))

;; Command execution


(defn redis-client?
  "Can we use `thing` as a redis client?"
  [thing]
  (satisfies? IRedis thing))
