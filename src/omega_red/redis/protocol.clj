(ns omega-red.redis.protocol
  "Deals with Jedis/Redis internal protocol not Clojure protocol"
  (:require
   [omega-red.codec :as codec]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   [redis.clients.jedis
    AbstractPipeline
    JedisPooled
    Protocol$Command
    Response]))

(defn- cmd-kw->cmd* [cmd-kw]
  (let [cmd-name (-> cmd-kw name str/upper-case)]
    (Protocol$Command/valueOf cmd-name)))

(def ^:private cmd-kw->cmd
  (memoize cmd-kw->cmd*))

(defn cmd+args->command-with-args [[cmd & args]]
  (let [proto-cmd (cmd-kw->cmd cmd)]
    [proto-cmd (into-array String (mapv codec/serialize args))]))

(defn execute*
  "Executes a single Redis command as vector of command and arguments.:
  (execute-raw! conn [:ping])
  (execute-raw conn [:set \"foo\" \"bar\"])"
  [^JedisPooled client cmd+args]
  {:pre [(seq cmd+args)]}
  (let [[proto-command command-args] (cmd+args->command-with-args cmd+args)
        result (JedisPooled/.sendCommand client
                                         ^Protocol$Command proto-command
                                         ^String/1 command-args)]
    (codec/deserialize result)))

(defn execute-pipeline*
  "Executes a pipeline of Redis commands as a sequence of vectors of commands and arguments:

  (execute-pipeline! conn [[:ping]
                           [:set \"foo\" \"bar\"]
                           [:get \"foo\"]
                           [:del \"foo\"]])
  "
  [^JedisPooled client cmds+args]
  {:pre [(seq cmds+args)
         (every? keyword? (map first cmds+args))]}
  (with-open [pipeline ^AbstractPipeline (.pipelined client)]
    (let [responses (mapv (fn [cmd+args]
                            (let [[proto-command command-args] (cmd+args->command-with-args cmd+args)]
                              (AbstractPipeline/.sendCommand pipeline
                                                             ^Protocol$Command proto-command
                                                             ^String/1 command-args)))
                          cmds+args)]
      (.sync pipeline)
      (->> responses
           (mapv Response/.get)
           (mapv codec/deserialize)))))
