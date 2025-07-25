(ns omega-red.lock
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [omega-red.redis :as redis]))

(defprotocol RedLock
  (acquire [this] [this opts] "Acquire the lock. Returns true if acquired, false otherwise.")
  (acquire-with-timeout [this] [this opts] "Like `acquire` but will wait for the lock to be available up to `acquire-timeout-ms` milliseconds.
   If the lock is not acquired within that time, returns false.")
  (renew [this] "Renew the lock. Returns true if renewed, false otherwise.")
  (release [this] "Release the lock. Returns true if released, false otherwise.")
  (get-id [this] "Get ID of the lock holder. Returns the ID of the lock holder.")
  (get-lock-holder-id [this] "Get ID of the lock holder. Returns the ID of the lock holder.")
  (is-lock-holder? [this] "Check if this instance is the lock holder. Returns true if it is, false otherwise.")
  (lock-expiry-in-ms [this] "Returns number of milliseconds until the lock expires. Nil if lock doesn't exist"))

(def ^:private not-blank? (complement str/blank?))

;; Implementation
;; Lua scripts to guarantee atomicty, although we could use transactions perhaps?

(def ^:const lock-script
  (slurp (io/resource "locks-scripts/lock.lua")))

(def ^:const release-script
  (slurp (io/resource "locks-scripts/release.lua")))

(def ^:const renew-script
  (slurp (io/resource "locks-scripts/renew.lua")))

;; XXX: because EVAL doesn't get auto-prefixed in omega-red, we have to do it manually
(defn try-acquire*
  "Try acquiring a lock, doesn't use timeouts - so it either acquires the lock or fails immediately.
   Returns true if acquired, false otherwise."
  [conn {:keys [lock-key lock-id expiry-ms]}]
  (let [prefixed-lock-key (redis/key (:key-prefix conn) lock-key)
        result (redis/execute conn [:eval lock-script 1 prefixed-lock-key lock-id (str expiry-ms)])]
    ;; is acquired?
    (and (number? result) (pos? result))))

(defn try-acquire-with-timeout*
  "Try acquiring a lock, with a timeout options"
  [conn {:keys [lock-key lock-id expiry-ms acquire-timeout-ms acquire-resolution-ms]}]

  {:pre [(not-blank? lock-key)
         (not-blank? lock-id)

         (pos? expiry-ms)
         (pos? acquire-timeout-ms)
         (pos? acquire-resolution-ms)]}
  (let [prefixed-lock-key (redis/key (:key-prefix conn) lock-key)]
    (loop [timeout acquire-timeout-ms]
      (let [result (redis/execute conn [:eval lock-script 1 prefixed-lock-key lock-id (str expiry-ms)])
            acquired? (and (number? result) (pos? result))]
        (if acquired?
          true
          ;; try acquiring
          (if (pos? (- timeout acquire-resolution-ms))
            (do
              (try
                (Thread/sleep ^long acquire-resolution-ms)
                (catch InterruptedException _e
                  ::no-op))
              (recur (- timeout acquire-resolution-ms)))
            false))))))

(defn release*
  [conn {:keys [lock-key lock-id expiry-ms]}]
  (let [prefixed-lock-key (redis/key (:key-prefix conn) lock-key)]
    (redis/execute conn [:eval release-script 1 prefixed-lock-key lock-id (str expiry-ms)])
    true))

(defn renew*
  [conn {:keys [lock-key lock-id expiry-ms]}]
  (let [prefixed-lock-key (redis/key (:key-prefix conn) lock-key)]
    (= 1 (redis/execute conn [:eval renew-script 1 prefixed-lock-key lock-id (str expiry-ms)]))))

(defn get-lock-holder-id*
  [conn {:keys [lock-key]}]
  (let [res (redis/execute conn [:hkeys lock-key])]
    (when (> (count res) 1)
      (throw (ex-info "Unexpected state: multiple lock holders detected for the given lock key." {:lock-key lock-key})))
    (first res)))

(defn lock-expiry-in-ms* [conn {:keys [lock-key]}]
  (when-let [expire-ts (redis/execute conn [:pexpiretime lock-key])]
    (let [now (System/currentTimeMillis)]
      (- expire-ts now))))

;; Component

;; TODO: enable use without a component?

;; TODO: move to map-as-component to drop dependency on component?
(defrecord RedisLock
    [conn ;; injected
     lock-key ;; shared key to 'lock' on
     expiry-ms ;; how long to keep the lock
     acquire-timeout-ms ;; how long to wait for the lock
     acquire-resolution-ms ;; how often to check for the lock

     ;; derived state
     lock-id ;; unique identifier for this lock holder
     ]
  component/Lifecycle
  (start [this]
    (assert (:conn this) "missing Jedis connection pool")
    (if (:lock-id this)
      this
      (assoc this
             :lock-id (str lock-key "-" (random-uuid)))))

  (stop [this]
    (release this)
    (assoc this :lock-key nil :lock-id nil))

  RedLock
  (acquire [this]
    (try-acquire* conn this))

  (acquire-with-timeout [this]
    (acquire-with-timeout this {}))

  (acquire-with-timeout [this {:keys [acquire-timeout-ms]}]
    (try-acquire-with-timeout* conn (cond-> this
                                      ;; override default acquire timeout if passed
                                      acquire-timeout-ms (assoc :acquire-timeout-ms acquire-timeout-ms))))

  (renew [this]
    (renew* conn this))

  (release [this]
    (release* conn this))

  (get-id [this]
    (:lock-id this))

  (get-lock-holder-id [this]
    (get-lock-holder-id* conn this))

  (is-lock-holder? [this]
    (let [lock-holder-id (get-lock-holder-id* conn this)]
      (= (:lock-id this) lock-holder-id)))

  (lock-expiry-in-ms [this]
    (lock-expiry-in-ms* conn this)))

(def ^:private default-expiry-ms
  (* 60 1000)) ;; 1 minute

(def ^:private
  default-acquire-timeout-ms
  (* 10 1000)) ;; 10 seconds

(def ^:private
  default-acquire-resolution-ms
  100) ;; 100ms

(defn create [{:keys [lock-key lock-id expiry-ms acquire-timeout-ms acquire-resolution-ms]
               :or {expiry-ms default-expiry-ms
                    acquire-timeout-ms default-acquire-timeout-ms
                    acquire-resolution-ms default-acquire-resolution-ms}}]
  {:pre [(not (str/blank? lock-key))
         (not (str/blank? lock-key))
         (> expiry-ms acquire-resolution-ms 0)
         (> acquire-timeout-ms acquire-resolution-ms 0)]}
  (map->RedisLock {:lock-key lock-key
                   :lock-id lock-id
                   :expiry-ms expiry-ms
                   :acquire-timeout-ms acquire-timeout-ms
                   :acquire-resolution-ms acquire-resolution-ms}))

(defmacro with-lock [lock & body]
  `(try
     (if (acquire-with-timeout ~lock)
       (let [ret# (do ~@body)]
         {:status ::acquired-and-released
          :result ret#})
       {:status ::not-acquired})
     (finally
       (release ~lock))))
