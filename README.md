# omega-red


[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/omega-red.svg)](https://clojars.org/org.clojars.lukaszkorecki/omega-red)


<img  src="https://uncannyxmen.net/sites/default/files/images/characters/omegared/omegared00.jpg" heighth="400px" align=right >

## Idiomatic Redis client for Clojure


### Features

- Hiccup-style command API
- Full Redis protocols support and connection pooling backed by Jedis
- built-in Component suport (but optional, see section below)
- automatic key prefixing for data stored in shared Redis instances
- transparent serialization/deserialization of Clojure data structures via Transit

### Non Goals

- no support for worker queue or pub/sub abstraction
- no implementation of Redis commands as functions
- no locking or other complex operations built on top of Redis

> [!NOTE]
> This repo takes over from the original [omega-red](https://github.com/nomnom-insights/nomnom.omega-red) since it received no updates for a long time.
> and most of the original authors are no longer working on it. This fork is a continuation of the project, with breaking changes.

#### Command API

Rather than implementing a function for each Redis command, Omega Red uses vector-based API:


``` clojure
[:command-as-keyword arg1 arg2 arg3 ...]
```

To send these commands to Redis, use `omega-red.redis/execute` or `omega-red.redis/execute-pipeline` functions. The former is for single commands, the latter is for pipeline operations.

- `(execute conn [:command + args])` - for  single commands
- `(exececute-pipeline conn [ [:command1 + args] [:command2 + args]...])` - for pipeline operations


where `conn` is an instance of a client component created with `omega-red.client/create`.

##### Implementation details

Jedis' internals are based on `sendCommand` method implemented in all connection/connection-like classes. This allows Omega Red to use
the same method to send commands to Redis, while keeping the efficient connection pooling and full Redis protocol support.

Omega Red will automatically serialize and deserialize Clojure data structures using Transit,
so you can pass Clojure data structures directly to Redis commands and receive them back when reading.


 > [!NOTE]
 > Only basic Clojure datas tructures are supported - strings, numbers, lists, vectors, maps and sets.
 > Currently serializing other types or Java classes is not supported.

#### Usage
To create a client Component, call `omega-red.client/create` with an arg map, the following options are accepted:

- `:uri` - full Redis connection URI
- `:key-prefix` - optional, a string or keywor to prefix all keys used in write & read commands issued by this client (see below)
- `:ping-on-start?` - optional, if set to `true`, the client will attempt to ping the Redis server on start
- `:connection-pool` - either instance of `JedisPoolConfig` or a map which configures the connection pool, the keys and their default values are:
   - `:max-total` - 100, usually a sane default even for small Redis instances
   - `:max-idle` - 50% of `max-total`
   - `:min-idle` - 10% of `max-total`
   - `:max-wait-millis` - default of -1, meaning wait indefinitely - *usually* safe to set, but it depends on your setup


Once the component is created and started, you can call `omega-red.redis/execute` with the component and a vector of Redis commands, like so:

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))



(def client (component/start
             (redis.client/create {:uri "redis://localhost:6379"})))



;; example usage:

(redis/execute client [:set "some-data" {:a :map "is" #{"supported"}}])
(redis/execute client [:get "some-data"]) ;; => {:a :map "is" #{"supported"}}


(redis/execute client [:sadd "some-set" "a" "b" "c"])
(into #{} (reddis/execute client [:smembers "some-set"])) ;; => #{"a" "b" "c"}


;; pipelining:

(redis/execute-pipeline client [[:set "a" "1"]
                                [:set "b" "2"]
                                [:set "c" "3"]])

(redis/execute client [:mget "a" "b" "c"] ; =>  ["1" "2" "3"]


;; to help with building keys, a `key` function is provided:

(redis/key "some" :cool "stuff" ) ;; => "some:cool:stuff"
(redis/key :my.domain/thing) ;; => "my.domain/thing"
 ```

##### Automatic key prefixing

Enforcing consistent key prefixes is often used when several applications share the same Redis instance. It's also
helpful if you need to version your keys or separate them by environment.
When key prefixing is configred, Omega Red will figure out for you which parts of Redis commands are keys
and will prefix them automatically. Auto-prefixing is enabled
by setting `:key-prefix` in options map when creating the component:

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))

(def srv1-client (component/start
                  (redis.client/redis-client {:uri "redis://localhost:6379"
                                              :key-prefix "srv1"})))

(def srv2-client (component/start
                  (redis.client/redis-client {:uri "redis://localhost:6379"
                                              :key-prefix ::srv2})))


(redis/execute srv1-client [:set "foo" "1"]) ;; => "OK", would set key "srv1:foo"
(redis/execute srv2-client [:set "foo" "2"]) ;; => "OK", would set key "srv2:foo"

;; HOWEVER:
(redis/execute srv1-client [:keys "foo*"]) ;; => [] - because of autoprefixing!
```


Automatic key prefixing is 100% safe as internally the client builds a command parser based on Redis' own command specification.

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

(let [conn (componet/start (redis.client/create {:uri "127.0.0.1:6379"}))
      ;; caching example
      fetch! (fn []
               (cache/get-or-fetch conn {:fetch (fn [] (slurp "http://example.com"))
                                         :cache-set (fn [conn fetch-res]
                                                      (redis/execute conn [:set "example" fetch-res "EX" 10])
                                         :cache-get (fn [conn]
                                                      (redis/execute conn [:get "example"]))}))]

  (fetch!) ;; => returns contents of http://example.com as a result of direct call
  (fetch!) ;; => pulls from cache
  (fetch!) ;; => pulls from cache
  (Thread/sleep (* 10 1000)) ;; wait 10s
  (fetch!) ;; => makes http request again

  ;; Convinence function for memoization:

  ;; memoize-replacement - DATA WILL STICK AROUND UNLESS SOMETHING ELSE DELETES THE KEY
  (cache/memoize conn  {:key "example.com"
                        :fetch-fn #(slurp "http://example.com")})


  ;; memoize with expiry
  (cache/memoize conn  {:key "example.com"
                        :fetch-fn #(slurp "http://example.com")
                        :expiry-s 30}))
```


##### Usage without Component

If you can't/don't want to use Component, you can use Omega Red without it. Create an instance of `Jedis` or `JedisPool` and
pass it to `execute` or `execute-pipeline` functions under `:pool` key:

```clojure
(import (java.clients.jedis Jedis))


(def client (omega-red.client/create {:uri "<ignore me>"}))


(with-open [jedis (Jedis. "redis://localhost:6379")]
  (omega-red.redis/execute {:pool jedis} [:set "foo" "bar"])
  (omega-red.redis/execute {:pool jedis} [:get "foo"]))
```

## Notes & Caveats


### Key prefixes and listing keys

When `:key-prefix` is set, Omega Red will prefix all keys in Redis commands with the value of `:key-prefix` - this is safe because Omega Red uses Redis' own command specification to implement key processing.
However, that doesn't apply to certain commands like `keys` or `scan` - return values of these commands will include a prefix, which might lead to some confusion, see this example:


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

### Keywords in commands

Due to how arguments are processed, commands which have special arguments like `EX` in `SET` or `NOVALUES` in `HSET` will not work as expected when passed as keywords.
To work around this use strings instead:


``` clojure
;; this won't work:

(r/execute conn [:set "foo" "bar" :ex 10]) ;; => "ERR syntax error"

;; this will:
(r/execute conn [:set "foo" "bar" "EX" 10]) ;; => "OK"
```


# Changelog

- 2.3.0 - **bugfix release**
  - adds connection pool configuration options
  - fix a bug in how command pipelines were executed which would cause a resource leak

- 2.2.0 - 2025/03/10
  - First stable release based on Jedis

- 2.2.0-SNAPSHOT - 2025/02/26  **Breaking changes**
  - migrates off Carmine to Jedis
  - internals updates, better separation of namespaces
  - support for auto key prefixing
  - better cache helpers
  - dependency updates

- 2.1.0-SNAPSHOT - 2025/02/07 - **Unreleased exploratory version** **Breaking changes**
 - refactors internals

- 2.0.0 - 2025/01/09 - **Breaking changes**:
  - takes over from the original repo, with a new Maven coordinate
  - changes namespace structure
  - proper connection pool management
  - faster implementation using Carmine's internals
  - dependency update
  - fixes to cache helper

- 1.1.0 - 2022/03/08 - Clean up and cache helper
- 1.0.2 - Dependency updates
- 1.0.0-SNAPSHOT - **Breaking change!** Changes signature of `execute` to accept a vector, and `execute-pipeline` to accept a vector of vectors. This makes it easier to work with variadic Redis commands (`hmset` etc) and compose commands
- 0.1.0- 2019/10/23 - Initial Public Offering

# Roadmap

- [x] explicit connection pool component with its own lifecycle
- [x] move off Carmine and use Jedis or Lettuce directly (because of the point above)
- [ ] more Jedis/Apache Pool configuration options
- [ ] improved command arg handling, to account for non-key arguments that can expressedp themselves as keywords
- [ ] metrics/OTel support
