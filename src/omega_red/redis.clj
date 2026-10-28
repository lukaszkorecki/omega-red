(ns omega-red.redis
  "Protocol for Redis command dispatch, and utilities for working with Redis keys"
  (:refer-clojure :exclude [key])
  (:require
   [omega-red.codec :as codec]))

(set! *warn-on-reflection* true)

;; NOTE: this is used, implicitly via map created via omega-red.client/create
;;       so clj-kondo warning is invalid!
(defprotocol IRedis
  :extend-via-metadata true
  (execute
    [this cmd+args]
    "Executes single redis command - passed as JDBC-style vector: [:command the rest of args]")
  (execute-pipeline
    [this cmds+args]
    "Executes a series of commands + their args in a pipeline. Commands are a vector of vecs with the commands and their args")
  (transact
    [this cmd+args]
    "Executes a series of commands + their args in a transaction (MULTI/EXEC). Commands are a vector of vecs with the commands and their args just like `execute-pipeline`
     NOTE: transactions are usually slower than pipelines, but commands are executed atomically, with no other client's commands interleaved.
     Redis does not roll back: if a command fails at runtime, the remaining commands are still applied."))

;; Command execution

(defn key
  "Simplifies working with keys that need to be programmatically built.
  e.g. rather than doing `(str some-thing \":\" some-id)` you can do
  (key some-thing some-id)"
  [& args]
  (codec/serialize-key args))

(defn valid-client?
  "Can we use `thing` as a Redis client?"
  [thing]
  (when-let [pool (-> thing :pool)]
    (instance? redis.clients.jedis.UnifiedJedis pool)))
