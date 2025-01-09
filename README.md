# omega-red


[![Clojars Project](https://img.shields.io/clojars/v/lukaszkorecki/omega-red.svg)](https://clojars.org/nomnom/omega-red)

<img  src="https://uncannyxmen.net/sites/default/files/images/characters/omegared/omegared00.jpg" heighth="400px" align=right >

## A Redis component


Wraps [Carmine](https://github.com/ptaoussanis/carmine) for better dev ergonomics.

### Why wrap?

- provides proper connection pool management, rather than using default memoized pool
- data-driven API for Redis commands
- *technically* makes it possible to swap underlying Redis client (Carmine) for something else, if needed while keeping the API the same


#### Design

The idea is that the component wraps the conection, and you pass "raw" Redis commands, like in the Redis shell or CLI, rather than invoking Carmine command functions + their arguments. The way it works is that  we convert the first keyword (command) to a Carmine function, cache it and then invoke it. The lookup/conversion is cached for later so that we don't pay the cost of the lookup too often.

If you want to mock the component - you'll need something that implements the following protocol (defined as  `omega-red.protocol.Redis`):

- `(execute this [:command + args])` - for  single commands
- `(exececute-pipeline this [ [:command1 + args] [:command2 + args]...])` - for pipeline operations

and fakes Redis behavior as needed.


##### Cache helper

Omega Red also supports a very simple "fetch from cache or populate on miss" workflow - it's not complicated but nicely DRYs up your code.
See example below


# Example

```clojure
(ns omega-red.redis-test
  (:require [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [com.stuartsierra.component :as component]))

(let [conn (componet/start (redis.client/create {:host "127.0.0.1" :port 6379}))]
    (is (= 0 (redis/execute conn [:exists "test.some.key"]))) ; true
    (is (= "OK" (redis/execute conn [:set "test.some.key" "foo"]))) ; true
    (is (= 1 (redis/execute conn [:exists "test.some.key"]))) ; true
    (is (= "foo" (redis/execute conn [:get "test.some.key"]))) ; true
    (is (= 1 (redis/execute conn [:del "test.some.key"]))) ; true
    (component/stop conn)
    (is (nil? (redis/execute conn [:get "test.some.key"])))) ; true

;; pipeline execution
(is (= [nil "OK" "oh ok" 1]
       (redis/execute-pipeline conn
                               [[:get "test.some.key.pipe"]
                                [:set "test.some.key.pipe" "oh ok"]
                                [:get "test.some.key.pipe"]
                                [:del "test.some.key.pipe"]]))) ; true


;; caching example
(let [fetch! (fn []
               (redis/cache-get-or-fetch conn {:fetch (fn [] (slurp "http://example.com"))
                                               :cache-set (fn [conn fetch-res]
                                                            (redis/execute conn [:setex "example" 10 fetch-res]))
                                               :cache-get (fn [conn]
                                                            (redis/exeucte conn [:get "example"]))}))]

  (fetch!) ;; => returns contents of http://example.com as a result of direct call
  (fetch!) ;; => pulls from cache
  (fetch!) ;; => pulls from cache
  (Thread/sleep (* 10 1000))
  (fetch!)) ;; => makes http request again
```




## Change log

- 1.1.0 - Clean up and cache helper
- 1.0.2 - Dependency update
- 1.0.0-SNAPSHOT - **Breaking change!** Changes signature of `execute` to accept a vector, and `execute-pipeline` to accept a vector of vectors. This makes it easier to work with variadic Redis commands (`hmset` etc) and compose commands
- 0.1.0- 2019/10/23 - Initial Public Offering

# Roadmap

- [ ] explicit connection pool component with its own lifecycle
- [ ] *maybe* move off Carmine and use Jedis or Lettuce directly (because of the point above)


# Authors

<sup>In alphabetical order</sup>

- [Afonso Tsukamoto](https://github.com/AfonsoTsukamoto)
- [Łukasz Korecki](https://github.com/lukaszkorecki)
- [Marketa Adamova](https://github.com/MarketaAdamova)
