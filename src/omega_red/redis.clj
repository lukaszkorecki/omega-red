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

(defn- cmd-kw->cmd* [cmd-kw]
  (let [cmd-name (-> cmd-kw name str/upper-case)]
    (Protocol$Command/valueOf cmd-name)))

(def ^:private cmd-kw->cmd
  (memoize cmd-kw->cmd*))

(defn cmd+args->command-with-args [[cmd & args]]
  (let [proto-cmd (cmd-kw->cmd cmd)]
    ;; FIXME: handle clojure data encoding!
    ;; use https://github.com/clojure/data.fressian (rather than Nippy because we don't want Encore baggage...)
    [proto-cmd (into-array String (mapv str args))]))

(defn read-result [res]
  (cond
    (number? res) res
    (bytes? res) (String. ^bytes res "UTF-8")
    ;; XXX: should we protect against recursion here?
    (instance? java.util.ArrayList res) (mapv read-result res)
    :else (do
            (r/pp {:r res :c (class res) :x (coll? res)})
            res)))

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
    (read-result result)))

(defn execute-pipeline*
  "Executes a pipeline of Redis commands as a sequence of vectors of commands and arguments:

  (execute-pipeline! conn [[:ping]
                           [:set \"foo\" \"bar\"]
                           [:get \"foo\"]
                           [:del \"foo\"]])
  "
  [^JedisPooled client cmds+args]
  {:pre [(seq cmds+args)
         (every? seq cmds+args)]}
  (let [pipeline ^AbstractPipeline (.pipelined client)
        responses (mapv (fn [cmd+args]
                          (let [[proto-command command-args] (cmd+args->command-with-args cmd+args)]
                            (AbstractPipeline/.sendCommand pipeline
                                                           ^Protocol$Command proto-command
                                                           ^String/1 command-args)))
                        cmds+args)]
    (.sync pipeline)
    (->> responses
         (mapv Response/.get)
         (mapv read-result))))

;; alias key to taoensso.carmine/key, but with docstring
;; FIXME: use our own implementation!
(def ^{:doc (:doc (meta (var taoensso.carmine/key)))}
  build-key
  taoensso.carmine/key)

;; See 'script' in dev-resources/omega_red/gen_cmd_config.clj for the code which generates this
;; The config is a map of:
;; { <cmd-kw> {:non-key-args-tail-count <int> :type <multi|single> :summary <str> :all-args <vec-of-str>}}
;; It basically tells us how many keys are in given command vector (one/many) and how many non-key args are at the end
;; so that it can be used to apply key prefixes, using 'key' function
(def redis-cmd-config
  (-> "redis-cmd-key-config.edn"
      io/resource
      slurp
      edn/read-string))

(defn apply-key-prefixes
  "Applies key prefix to the command and its arguments.
  It detects if given command accepts a key or a variadic number of keys
  and applies prefixes to them.
  NOTE: in most cases, the key is the first argument, followed by
  multiple keys with no extra arguments, and finally in very few cases
  a list of keys + optional arg is accepted. This fn applies prefixes to the keys only.
  "
  [{:keys [key-prefix]} cmd+args]
  (if-let [cmd-key-conf (and key-prefix
                             (get redis-cmd-config (first cmd+args)))]
    (let [{:keys [non-key-args-tail-count type]} cmd-key-conf]
      (cond
        ;; easy - 1st arg is key
        (= :single type) (update-in cmd+args [1] #(build-key key-prefix %))

        ;; a bit more complex - all args are keys
        (and (= :multi type) (zero? non-key-args-tail-count))
        (vec (concat [(first cmd+args)] (map #(build-key key-prefix %) (rest cmd+args))))

        ;; worst case scenario
        (and (= :multi type) (pos? non-key-args-tail-count))
        (let [[cmd & args] cmd+args]
          (vec
           (concat [cmd]
                   (map #(build-key key-prefix %) (drop-last non-key-args-tail-count args))
                   (drop (- (count args) non-key-args-tail-count) args))))
        :else
        (throw (ex-info "not sure how to deal with this" {:cmd+args cmd+args}))))
    ;; no key to deal with
    cmd+args))

(defn redis-client?
  "Can we use `thing` as a redis client?"
  [thing]
  (satisfies? IRedis thing))
