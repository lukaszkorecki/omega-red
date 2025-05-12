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
    AbstractTransaction
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
    ;; FIXME: handle clojure data encoding!
    ;; use https://github.com/clojure/data.fressian (rather than Nippy because we don't want Encore baggage...)
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

(defn transact*
  "Executes a transaction of Redis commands as a sequence of vectors of commands and arguments:

  (execute-pipeline! conn [[:ping]
                           [:set \"foo\" \"bar\"]
                           [:get \"foo\"]
                           [:del \"foo\"]])
  "

  [^JedisPooled client cmds+args]
  {:pre [(seq cmds+args)
         (every? keyword? (map first cmds+args))]}
  (with-open [tx ^AbstractTransaction (.multi client)]
    (let [responses (mapv (fn [cmd+args]
                            (let [[proto-command command-args] (cmd+args->command-with-args cmd+args)]
                              (AbstractPipeline/.sendCommand tx
                                                             ^Protocol$Command proto-command
                                                             ^String/1 command-args)))
                          cmds+args)]
      (.exec tx)
      (->> responses
           (mapv Response/.get)
           (mapv codec/deserialize)))))

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

(defn- with-prefix [key-prefix]
  (fn with-prefix'
    [a-key]
    (codec/serialize-key [key-prefix a-key])))

;; XXX: perhaps this could be all-in-one: key prefixing +  arg-preparation operation?
(defn apply-key-prefixes
  "Applies key prefix to the command and its arguments.
  It detects if given command accepts a key or a variadic number of keys
  and applies prefixes to them.
  NOTE: in most cases, the key is the first argument, followed by
  multiple keys with no extra arguments, and finally in very few cases
  a list of keys + optional arg is accepted. This fn applies prefixes to the keys only.

  NOTE: we have this here rather than in `codec` namespace since it deals with Redis' protocol/commands"
  [{:keys [key-prefix]} cmd+args]
  (if-let [cmd-key-conf (get redis-cmd-config (first cmd+args))]
    (let [{:keys [non-key-args-tail-count type]} cmd-key-conf]
      (cond
        ;; easy - 1st arg is key - this is the majority scenario
        (= :single type) (update-in cmd+args [1] (with-prefix key-prefix))

        ;; a bit more complex - all args are keys
        (and (= :multi type) (zero? non-key-args-tail-count))
        (vec (concat [(first cmd+args)] (mapv (with-prefix key-prefix) (rest cmd+args))))

        ;; worst case scenario
        (and (= :multi type) (pos? non-key-args-tail-count))
        (let [[cmd & args] cmd+args]
          (vec
           (concat [cmd]
                   (mapv (with-prefix key-prefix) (drop-last non-key-args-tail-count args))
                   (drop (- (count args) non-key-args-tail-count) args))))
        :else
        (throw (ex-info "not sure how to deal with this" {:cmd+args cmd+args}))))
    ;; no key to deal with
    cmd+args))
