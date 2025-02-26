# omega-red


[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/omega-red.svg)](https://clojars.org/org.clojars.lukaszkorecki/omega-red)


<img  src="https://uncannyxmen.net/sites/default/files/images/characters/omegared/omegared00.jpg" heighth="400px" align=right >

## A Redis client for Clojure with flexible API


> [!NOTE]
> This repo takes over from the original [omega-red](https://github.com/nomnom-insights/nomnom.omega-red) since it received no updates for a long time.
> The original repo is still available for reference.

### What's inside?

- wraps Jedis Redis client with a more Clojure-friendly API
- provides proper connection pool management
- data-driven API for Redis commands
- supports Component out of the box, but is not tied to it (see below)

#### Design

The idea is that the component wraps the conection, and you pass "raw" Redis commands, like in the Redis shell or CLI, rather than invoking specific methods on `UnifiedJedis` interface, representing various Redis commands. This is possible by calling `sendCommand` method rather than dynamiclly dispatching to methods.

If you want to mock the component - you'll need something that implements the following protocol (defined in `omega-red.redis.IRedis`):

- `(execute this [:command + args])` - for  single commands
- `(exececute-pipeline this [ [:command1 + args] [:command2 + args]...])` - for pipeline operations

and fakes Redis behavior as needed.


#### Usage


To create a client Component, call `omega-red.client/create` with a map of arguments, the following keys are accepted:

- `:uri` - full Redis connection URI
- `:key-prefix` - optional, a string or keywor to prefix all keys used in write & read commands issued by this client (see below)

- **TODO** connection pool settings


Once the component is created and started, you can call `omega-red.redis/execute` with the component and a vector of Redis commands, like so:

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))


(defn some-ring-handler [{:keys [component uri]}]
  (let [from-cache (redis/execute (:redis component) [:get "very-important-data"])]
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body from-cache}))

 ```

 This is of course a very simple example, but it shows the basic example. Omega Red will transparently handle connection pooling and lifecycle of the connection, as
 well as ensuring that Clojure data structures are correctly serialized and deserialized.

 > [!NOTE]
 > Only basic Clojure datastructures are supported - strings, numbers, lists, vectors, maps and sets. If you need to store more complex data,
 > you'll need to serialize it yourself, as Redis only supports strings as values.


##### Key prefixes

In a lot of environment you'll want to prefix keys in case of shared Redis instances. Omega Red will figure out for you which parts of Redis commands
are keys and will prefix them automatically for you. Auto-prefixing is enabled by setting `:key-prefix` in options map when creating the component:

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

##### Cache helper

Omega Red also supports a very simple "fetch from cache or populate on miss" workflow - it's not complicated but nicely DRYs up your code.
See example below


###### Example

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
                                                      (redis/execute conn [:setex "example" 10 fetch-res]))
                                         :cache-get (fn [conn]
                                                      (redis/exeucte conn [:get "example"]))}))]

  (fetch!) ;; => returns contents of http://example.com as a result of direct call
  (fetch!) ;; => pulls from cache
  (fetch!) ;; => pulls from cache
  (Thread/sleep (* 10 1000))
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


###### Usage without Component

If you don't want to use Component, you can use Omega Red without it. Just create a client and pass an instance of `Jedis` or `JedisPooled` class under `:pool` key:

```clojure
(import (java.clients.jedis Jedis))


(def client (omega-red.client/create {:uri "<ignore me>"}))


(with-open [jedis (Jedis. "redis://localhost:6379")]
  (omega-red.redis/execute {:pool jedis} [:set "foo" "bar"])
  (omega-red.redis/execute {:pool jedis} [:get "foo"]))
```


## Change log

- 2.1.0 - **Unreleased**, **Breaking changes**
  - internals updates, better separation of namespaces
  - support for auto key prefixing
  - better cache helpers
  - dependency updates
  - migrates off Carmine to Jedis

- 2.0.0 - **Breaking changes**:
  - takes over from the original repo, with a new Maven coordinate
  - changes namespace structure
  - proper connection pool management
  - faster implementation using Carmine's internals
  - dependency update
  - fixes to cache helper
- 1.1.0 - Clean up and cache helper
- 1.0.2 - Dependency update
- 1.0.0-SNAPSHOT - **Breaking change!** Changes signature of `execute` to accept a vector, and `execute-pipeline` to accept a vector of vectors. This makes it easier to work with variadic Redis commands (`hmset` etc) and compose commands
- 0.1.0- 2019/10/23 - Initial Public Offering

# Roadmap

- [x] explicit connection pool component with its own lifecycle
- [x] *maybe* move off Carmine and use Jedis or Lettuce directly (because of the point above)


# Original Authors

<sup>In alphabetical order</sup>

- [Afonso Tsukamoto](https://github.com/AfonsoTsukamoto)
- [Łukasz Korecki](https://github.com/lukaszkorecki)
- [Marketa Adamova](https://github.com/MarketaAdamova)
