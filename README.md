# omega-red


[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/omega-red.svg)](https://clojars.org/org.clojars.lukaszkorecki/omega-red)


<img  src="https://uncannyxmen.net/sites/default/files/images/characters/omegared/omegared00.jpg" height="400px" align=right >

## Idiomatic Redis client for Clojure


### Features

- HoneySQL-style command API
- Full Redis protocols support and connection pooling backed by Jedis
- built-in Component support (but optional, see section below)
- automatic key prefixing for data stored in shared Redis instances
- transparent serialization/deserialization of Clojure data structures via Transit

### Non Goals

- no support for worker queue or pub/sub abstraction
- no special support for converting from Redis data structures to Clojure data structures
- no support for Redis Streams, Lua scripts or other advanced features

> [!NOTE]
> This repo takes over from the original [omega-red](https://github.com/nomnom-insights/nomnom.omega-red) since it received no updates for a long time
> and most of the original authors are no longer working on it. This fork is a continuation of the project with breaking changes.

### Migrating to v3

v3 upgrades to Jedis 8, which removed `JedisPooled` and replaced it with `RedisClient`. Omega Red's own API is unchanged:

- if you pass `:connection-pool` as a **map**, nothing to do
- if you pass a pool config **instance**, swap `JedisPoolConfig` for `redis.clients.jedis.ConnectionPoolConfig` - it's the type Jedis 8 builders expect
- if you reach for the underlying client directly, it's now a `RedisClient` rather than a `JedisPooled` - both extend `UnifiedJedis`, so `sendCommand`, `pipelined` and `multi` are unchanged
- connection pooling behaves exactly as before - same commons-pool2 settings, same reuse semantics


#### Command API

Rather than implementing a function for each Redis command, Omega Red uses a vector-based API:


``` clojure
[:command-as-keyword arg1 arg2 arg3 ...]
```

To send these commands to Redis, use `omega-red.redis/execute`, `omega-red.redis/execute-pipeline` or `omega-red.redis/transact` functions.

- `(execute conn [:command + args])` - for  single commands
- `(execute-pipeline conn [ [:command1 & args] [:command2 & args]...])` - for pipeline operations - increases performance by sending and reading
  multiple commands in one go, but doesn't come with any consistency guarantees
- `(transact conn [ [:command1 & args] [:command2 & args]...])` - for transactions - all commands are executed atomically via `MULTI`/`EXEC`.
  Note that Redis [does not roll back](https://redis.io/docs/latest/develop/interact/transactions/) - if a command fails at runtime, the remaining commands are still applied.


where `conn` is an instance of a client component created with `omega-red.client/create`.

##### Implementation details

Jedis' internals are based on `sendCommand` method implemented in all connection/connection-like classes. This allows Omega Red to use
the same method to send commands to Redis, while keeping the efficient connection pooling and full Redis protocol support.

Omega Red will automatically serialize and deserialize Clojure data structures using Transit,
so you can pass Clojure data structures directly to Redis commands and receive them back when reading.


 > [!NOTE]
 > Only basic Clojure data structures are supported - strings, numbers, booleans, keywords, lists, vectors, maps and sets.
 > Currently serializing other types or Java classes is not supported.
 > Top-level strings, numbers and booleans are sent as plain strings, so they come back as strings: `1` is read back as `"1"`, `true` as `"true"`.
 > Everything else is serialized with Transit and read back as Clojure data.

#### Usage
To create a client Component, call `omega-red.client/create` with an arg map, the following options are accepted:

- `:uri` - full Redis connection URI
- `:key-prefix` - optional, a string or keyword to prefix all keys used in write & read commands issued by this client (see below)
- `:ping-on-start?` - optional, if set to `true`, the client will attempt to ping the Redis server on start
- `:connection-pool` - either instance of `ConnectionPoolConfig` or a map which configures the connection pool. When omitted, `{:max-total 100}` is used. The keys and their default values are:
   - `:max-total` - required when passing a map, 100 is usually a sane default even for small Redis instances
   - `:max-idle` - 50% of `max-total`, at least 1
   - `:min-idle` - 10% of `max-total`, at least 1
   - `:max-wait-millis` - default of -1, meaning wait indefinitely - *usually* safe to set, but it depends on your setup


Once the component is created and started, you can call `omega-red.redis/execute` with the component and a vector of Redis commands, like so:

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))


(def client (component/start
             (redis.client/create {:uri "redis://localhost:6379"})))

;; simple commands, with transparent clojure data serialization
(redis/execute client [:set "some-data" {:a :map "is" #{"supported"}}])
(redis/execute client [:get "some-data"]) ;; => {:a :map "is" #{"supported"}}

;; no magic here, just clojure data
(redis/execute client [:sadd "some-set" "a" "b" "c"])
(into #{} (redis/execute client [:smembers "some-set"])) ;; => #{"a" "b" "c"}


;; pipelining:
(redis/execute-pipeline client [[:set "a" "1"]
                                [:set "b" "2"]
                                [:set "c" "3"]])

(redis/execute client [:mget "a" "b" "c"]) ; =>  ["1" "2" "3"]

;; transactions
(redis/transact client [[:set "a" "1"]
                        [:set "b" "2"]
                        [:set "c" "3"]])

(redis/execute client [:mget "a" "b" "c"]) ; =>  ["1" "2" "3"]


;; to help with building keys, a `key` function is provided:

(redis/key "some" :cool "stuff" ) ;; => "some:cool:stuff"
(redis/key :my.domain/thing) ;; => "my.domain/thing"

;; this way you can do things like this:

(redis/execute-pipeline client [[:get (redis/key ::widgets some-id)]
                                [:hgetall (redis/key ::counters)]])
 ```
##### 'Tokens' in commands

Redis' specs use the term 'token' to describe the arguments of a command. Some commands support named arguments, such as `SET`'s `EX` or `NX`.
To make working with the DSL more convenient, Omega Red supports passing these tokens as strings or keywords, so you can write:

```clojure
(redis/execute client [:set "foobar" :ex 10]) ;; => "OK"
```


##### Automatic key prefixing

Enforcing consistent key prefixes is often used when several applications share the same Redis instance. It's also
helpful if you need to version your keys or separate them by environment/workload to avoid collisions.

When key prefixing is configured, Omega Red will automatically determine which parts of Redis commands are keys
and apply the prefix accordingly.

> [!WARNING]
> Automatic key prefixing is implemented by using Redis' own command specification to determine which arguments are keys, however it's not perfect.
> Due to inconsistencies in Redis specs and general lack of information about how command processing should be implemented,
> 100% command coverage is not guaranteed. If you find a command that doesn't work as expected, please file an issue.
> Currently known commands that **are not** prefixed are `EVAL`, `SCRIPT` and others. To find out which commands are supported,
> eval this in the REPL:
>
> ```clojure
> (require '[omega-red.redis.command :as cmd])
> (->> cmd/key-processors
>      (remove #(= cmd/no-op-prefixer (val %)))
>      keys
>      sort)
> ```
>
> `key-processors` also contains commands with key arguments in positions Omega Red can't determine yet - these are passed through as-is.

Auto-prefixing is enabled by setting `:key-prefix` in the options map when creating the client component:

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))

(def srv1-client (component/start
                  (redis.client/create {:uri "redis://localhost:6379"
                                        :key-prefix "srv1"})))

(def srv2-client (component/start
                  (redis.client/create {:uri "redis://localhost:6379"
                                        :key-prefix ::srv2})))


(redis/execute srv1-client [:set "foo" "1"]) ;; => "OK", would set key "srv1:foo"
(redis/execute srv2-client [:set "foo" "2"]) ;; => "OK", sets key "srv2:foo"

;; HOWEVER:
(redis/execute srv1-client [:keys "foo*"]) ;; => [] - because of auto-prefixing!
```

##### Cache utils

Omega Red provides helpers for common use cases, such as "return from cache on hit or fetch from data source and populate on miss" workflow.
These helpers are provided by `omega-red.cache` namespace.
Example:

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.cache :as cache]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))

(let [conn (component/start (redis.client/create {:uri "redis://127.0.0.1:6379"}))
      ;; caching example
      fetch! (fn []
               (cache/get-or-fetch conn {:fetch (fn [] (slurp "http://example.com"))
                                          :cache-set (fn [conn fetch-res]
                                                       (redis/execute conn [:set "example" fetch-res "EX" 10]))
                                         :cache-get (fn [conn]
                                                      (redis/execute conn [:get "example"]))}))]

  (fetch!) ;; => returns contents of http://example.com as a result of direct call
  (fetch!) ;; => pulls from cache
  (fetch!) ;; => pulls from cache
  (Thread/sleep (* 10 1000)) ;; wait 10s
  (fetch!) ;; => makes http request again

  ;; Convenience function for memoization:

  ;; memoize-replacement - DATA WILL STICK AROUND UNLESS SOMETHING ELSE DELETES THE KEY
  (cache/memoize conn  {:key "example.com"
                        :fetch-fn #(slurp "http://example.com")})


  ;; memoize with expiry
  (cache/memoize conn  {:key "example.com"
                        :fetch-fn #(slurp "http://example.com")
                        :expiry-s 30}))
```
##### Locks

A lock Component is provided. It uses Lua scripts to implement locking and unlocking. Implementation is based on Carmine's and jedis-tools implementation.

> [!NOTE]
> Redis locks are Good Enough :tm: for most use cases, but they are not perfect. They're very effective when using a single Redis instance, however
> clustered deployments are not guaranteed to behave correctly. For clustered environments, consider a distributed lock implementation based on Consul, Zookeeper, or even Postgres-based optimistic locking.


Options supported by `omega-red.lock/create`:

- `:lock-key` - the key to use for the lock, e.g. "db-migration" or "widget-data-sync". If the client has a `:key-prefix` set, the prefix will be applied to the lock key
- `:expiry-ms` - the lock expiry time in milliseconds, default is 1 minute, this is the upper bound for how long the lock will be held, even if the process holding it dies
- `:acquire-timeout-ms` - the time to wait for the lock to be acquired, default is 10 seconds
- `:acquire-resolution-ms` - the time to wait between attempts to acquire the lock, default is 100ms
- `:release-on-stop?` - release the lock when the component is stopped, default is `true`
- `:lock-id` - optional, the ID identifying this lock holder - by default it's generated on component start as `<lock-key>-<random uuid>`

The API for working with locks is a set of functions:

- `(acquire lock)` - acquire the lock for `expiry-ms` milliseconds, returns `true` if the lock was acquired, `false` otherwise. This is a non-blocking call.
- `(acquire-with-timeout lock)` - same as `acquire` but will wait for the lock to be available up to `acquire-timeout-ms` milliseconds.
  - `(acquire-with-timeout lock {:acquire-timeout-ms 5000})` - allows for overriding the default `:acquire-timeout-ms` value.
- `(release lock)` - immediately release the lock, always returns `true`
- `(renew lock)` - if the lock instance is the holder, it can be renewed, this will extend the lock expiry time to `expiry-ms` milliseconds from now


The following functions can be used to inspect the state of a lock:

- `(is-lock-holder? lock)` - check if current instance is the lock holder
- `(lock-expiry-in-ms lock)` - check how long the lock will be held for, in milliseconds. Returns a negative number if the lock doesn't exist
- `(get-id lock)` - the lock ID of this instance
- `(get-lock-holder-id lock)` - the lock ID of the current lock holder, `nil` if the lock is not held

For tests, `(omega-red.lock/create-mock {:always-acquire? true})` returns a mock implementing the same protocol, without talking to Redis.
`:always-acquire?` controls whether `acquire` and `acquire-with-timeout` succeed, default is `true`.


As a best practice, acquire the lock just long enough to do the necessary work, and release it as soon as possible. If your code anticipates work taking longer than the initial expiry time, use `renew` to extend the lease.

A lock holder can call `acquire` again while holding the lock - it succeeds and refreshes the expiry. A single `release` releases the lock regardless of how many times it was acquired, so nested `with-lock` calls on the same lock release it when the innermost one finishes.

The `with-lock` macro implements a simple `acquire-with-timeout` + `finally release` pattern. It returns a map of `{:status .. :?result }`

```clojure
(require '[com.stuartsierra.component :as component]
         '[omega-red.client]
         '[omega-red.lock :as lock])

(def sys-map
  {:conn (omega-red.client/create {:uri "redis://localhost:6379"})
   :lock (component/using
          (omega-red.lock/create {:lock-key "my-db-migration-sync-process"})
          [:conn])})

;; .... get the system working....

(let [{:keys [lock]} @sys]
  (when (omega-red.lock/acquire-with-timeout lock)
    (try
      ;; do your db migration here
      (finally
        (omega-red.lock/release lock)))))

;; there's a convenient macro for using the lock

(lock/with-lock lock
  ;; do the db ops here
  )
;; => {:status :omega-red.lock/acquired-and-released :result ...}  when work was performed
;; or {:status :omega-red.lock/not-acquired }  if the lock was not acquired


;; Usage without a system map - the lock still needs a started Omega Red client

(let [conn (component/start (omega-red.client/create {:uri "redis://localhost:6379"}))
      lock (-> (omega-red.lock/create {:lock-key "my-db-migration-sync-process"})
               (assoc :conn conn)
               component/start)]
  (when (omega-red.lock/acquire-with-timeout lock)
    (try
      ;; do your db migration here
      (finally
        (omega-red.lock/release lock)))))

```


##### Redis client usage without Component

If you can't/don't want to use Component, you can use Omega Red without it. Create an instance of `RedisClient` (with or without a connection pool), and use `omega-red.redis.protocol` namespace to invoke `execute*`, `transact*` or `execute-pipeline*`.

See `client_test.clj` for a runnable example.

> [!WARNING]
> Using Omega Red this way works, but bypasses key prefixing and command processing.
> You'll have to use `omega-red.redis.command` for that.
> This approach is best suitable for:
> - quick evals and poking around Redis state
> - if you want to add an adapter for other Component-like library

## Notes & Caveats

### Key prefixes and listing keys

When `:key-prefix` is set, Omega Red will prefix all keys in Redis commands with the value of `:key-prefix` - this is safe because Omega Red uses Redis' own command specification to implement key processing.
However, the return values of commands like `keys` or `scan` will include the prefix, which might lead to some confusion. See this example:


``` clojure
;; assuming `conn` was created with a key prefix of "bananas":
(r/execute conn [:set "foo:bar" "baz"])
(r/execute conn [:set "foo:baz" "qux"])

;; works as expected:
(r/execute conn [:keys "foo:*"]) ;; => ["bananas:foo:bar" "bananas:foo:baz"]


;; however, if you want to use output of `keys` to do something, you'll need to strip the prefix yourself, otherwise
;; this happens:

(->> (r/execute conn [:keys "foo:*"])
    (mapv #(r/execute conn [:type %])))
;; => ["none" "none"]

;; to make this work, you'll need to strip the prefix yourself:
(->> (r/execute conn [:keys "foo:*"])
    (mapv #(r/execute conn [:type (str/replace % #"bananas:" "" )])))
;; => ["string" "string"]
```


# Development

Tooling (Java, Leiningen, clojure-lsp) is managed with [mise](https://mise.jdx.dev): run `mise install`, then `mise tasks` to list available tasks:

- `mise run test` - regenerates the command config and runs the test suite, requires Redis or Valkey running on `localhost:6379` (override with `REDIS_HOST`/`REDIS_PORT`)
- `mise run prep-resources` - regenerates `resources/redis-commands.edn` from Redis' command spec, same as `lein gen-cmd-config`
- `mise run fmt` / `mise run lint` - format and lint with clojure-lsp
- `mise run deploy` - regenerates the command config and deploys to Clojars

# Changelog

- [3.0.2](https://github.com/lukaszkorecki/omega-red/tree/v3.0.2)
  - **Bugfix** - `:connected?` is now `:omega-red.client/unknown` (was misspelled as `:omega-red.client/unkown`) when `:ping-on-start?` is not set
  - Documentation clean up and updates

- [3.0.1](https://github.com/lukaszkorecki/omega-red/tree/v3.0.1)
  - **Bugfix** - hash map reads are now correctly decoded. Jedis 8 changed return type. See [PR #13](https://github.com/lukaszkorecki/omega-red/pull/13)

- [3.0.0](https://github.com/lukaszkorecki/omega-red/releases/tag/v3.0.0) - **Breaking changes**
  - upgrades internals to Jedis 8 (see 'Migrating to v3' section)
  - refreshed command specs for compatibility with Redis 8

- [2.6.0](https://github.com/lukaszkorecki/omega-red/releases/tag/v2.6.0)
  - Added mock lock component for testing
  - Added `release-on-stop?` option to lock component
  - Dependency updates and refreshed Redis command spec
- [2.5.0](https://github.com/lukaszkorecki/omega-red/releases/tag/v2.5.0)
  - Added a distributed lock Component and a `with-lock` macro
  - Improved command processing and key prefixing
  - Added transaction support
- [2.3.0](https://github.com/lukaszkorecki/omega-red/releases/tag/v2.3.0) - **bugfix release**
  - Added connection pool configuration options
  - Fixed a bug in how command pipelines were executed which would cause a resource leak

- [2.2.0](https://github.com/lukaszkorecki/omega-red/releases/tag/v2.2.0)
  - First stable release based on Jedis

- 2.2.0-SNAPSHOT - **Breaking changes**
  - Migrated from Carmine to Jedis
  - Internal updates with better separation of namespaces
  - Support for auto key prefixing
  - Better cache helpers
  - Dependency updates

- 2.1.0-SNAPSHOT - **Unreleased exploratory version** - **Breaking changes**
  - Refactored internals

- [2.0.0](https://github.com/lukaszkorecki/omega-red/releases/tag/v2.0.0) - **Breaking changes**
  - Took over from the original repo, with a new Maven coordinate
  - Changed namespace structure
  - Proper connection pool management
  - Faster implementation using Carmine's internals
  - Dependency updates
  - Fixes to cache helper

- 1.1.0 - Clean up and cache helper
- 1.0.2 - Dependency updates
- 1.0.0-SNAPSHOT - **Breaking change!** Changes signature of `execute` to accept a vector, and `execute-pipeline` to accept a vector of vectors. This makes it easier to work with variadic Redis commands (`hmset` etc) and compose commands
- 0.1.0 - Initial Public Offering

# Roadmap

- [x] explicit connection pool component with its own lifecycle
- [x] move off Carmine and use Jedis or Lettuce directly (because of the point above)
- [ ] more Jedis/Apache Pool configuration options
- [x] improved command arg handling, to account for non-key arguments that can express themselves as keywords
- [x] metrics/OTel support - **done** Jedis is automatically instrumented when OTel Agent is provided
