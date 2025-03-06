(ns omega-red.redis
  "Protocol for redis dispatch, and utilities for working with Redis, caching being the core use case"
  (:refer-clojure :exclude [key])
  (:require
   [omega-red.codec :as codec]))

(set! *warn-on-reflection* true)

(defprotocol IRedis
  :extend-via-metadata true
  (execute
    [this cmd+args]
    "Executes single redis command - passed as JDBC-style vector: [:command the rest of args]")
  (execute-pipeline
    [this cmds+args]
    "Executes a series of commands + their args in a pipeline. Commands are a vector of vecs with the commands and their args. Use omega-red.protocol/excute-pipeline to invoke!")

  (inspect [this]
    "Returns a map of the current state of the connection"))

;; Command execution

(defn key
  "Simplifies working with keys that need to be progrmatically built.
  e.g. rather than doing `(str some-thing \":\" some-id)` you can do
  (key some-thing some-id)"
  [& args]
  (codec/serialize-key args))

(defn redis-client?
  "Can we use potentially `thing` as a redis client?"
  [thing]
  (inspect thing))
