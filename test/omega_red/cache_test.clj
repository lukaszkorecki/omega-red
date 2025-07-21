(ns omega-red.cache-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [omega-red.test-util :as tu]
   [omega-red.redis :as redis]
   [omega-red.cache :as redis.cache]))

(def state (atom 0))

(defn stateful []
  (str (swap! state inc)))

(use-fixtures :each (fn [test]
                      (tu/with-test-system (fn []
                                             (reset! state 0)
                                             (test)))))

(deftest fetch-or-get-test
  (testing "caches result of fetch call"
    (let [get-or-fetch #(redis.cache/get-or-fetch (tu/conn) {:fetch stateful
                                                             :cache-get (fn cache-get' [conn]
                                                                          (when-let [val (redis/execute conn [:get "testing:1"])]
                                                                            val))
                                                             :cache-set (fn cache-set' [conn fetch-result]
                                                                          (redis/execute conn [:set "testing:1" fetch-result]))})]
      (is (= "1" (get-or-fetch)))
      (testing "continous calls to stateful change the state but cached value is the same"
        (is (= "2" (stateful)))
        (is (= "3" (stateful)))

        (is (= "1" (get-or-fetch))))

      (testing "cache is invalidated somehow"
        (redis/execute (tu/conn) [:del "testing:1"])
        ;; new state is returned!
        (is (= "4" (get-or-fetch)))
        (is (= "5" (stateful))))))

  (testing "different data types"
    (let [get-or-fetch #(redis.cache/get-or-fetch (tu/conn) {:fetch (fn [] (stateful))
                                                             :cache-get (fn cache-get' [conn]
                                                                          (redis/execute conn [:hget "testing:2" "foo"]))
                                                             :cache-set (fn cache-set' [conn fetch-result]
                                                                          (redis/execute conn [:hset "testing:2" "foo" fetch-result]))})]
      ;; increments again because we're checking a different cache key!
      (is (= "6" (get-or-fetch)))
      (is (= "6" (get-or-fetch)))
      (is (= "7" (stateful)))
      (is (= "6" (get-or-fetch)))
      (redis/execute (tu/conn) [:del "testing:2"])
      (is (= "8" (get-or-fetch))))))

(deftest memoize-with-ex-test

  (testing "stores value in Redis and expires after N s"
    (is (= "1" (redis.cache/memoize (tu/conn) {:key "bananas"
                                               :fetch-fn stateful
                                               :expiry-s 1})))
    (is (= "1" (redis.cache/memoize (tu/conn) {:key "bananas"
                                               :fetch-fn stateful
                                               :expiry-s 1})))
    (is (= "1" (redis.cache/memoize (tu/conn) {:key "bananas"
                                               :fetch-fn stateful
                                               :expiry-s 1})))
    (is (= "1" (redis.cache/memoize (tu/conn) {:key "bananas"
                                               :fetch-fn stateful
                                               :expiry-s 1})))
    (Thread/sleep 1000)
    (is (= "2" (redis.cache/memoize (tu/conn) {:key "bananas"
                                               :fetch-fn stateful
                                               :expiry-s 1})))
    (is (= "2" (redis.cache/memoize (tu/conn) {:key "bananas"
                                               :fetch-fn stateful
                                               :expiry-s 1})))))
