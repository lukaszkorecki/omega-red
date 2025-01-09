(ns omega-red.cache-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [omega-red.test-util :as tu]
   [omega-red.protocol :as redis]
   [omega-red.redis]))

(def state (atom 0))

(defn stateful []
  (swap! state inc))

(use-fixtures :once (fn [test]
                      (tu/with-test-system (fn []
                                             (reset! state 0)
                                             (redis/execute (tu/conn) [:del "testing:1"])
                                             (redis/execute (tu/conn) [:del "testing:2"])
                                             (test)))))

(deftest cache-test
  (testing "caches result of fetch call"
    (let [get-or-fetch #(redis/cache-get-or-fetch (tu/conn) {:fetch stateful
                                                             :cache-get (fn cache-get' [conn]
                                                                          (when-let [val (redis/execute conn [:get "testing:1"])]
                                                                            (parse-long val)))
                                                             :cache-set (fn cache-set' [conn fetch-result]
                                                                          (redis/execute conn [:set "testing:1" fetch-result]))})]
      (is (= 1 (get-or-fetch)))
      (testing "continous calls to stateful change the state but cached value is the same"
      (is (= 2 (stateful)))
      (is (= 3 (stateful)))

      (is (= 1 (get-or-fetch))))

      (testing "cache is invalidated invalidation scenario"
        (redis/execute (tu/conn) [:del "testing:1"])
        ;; new state is returned!
        (is (= 4 (get-or-fetch)))
        (is (= 5 (stateful))))))

  (testing "different data types"
    (let [get-or-fetch #(redis/cache-get-or-fetch (tu/conn) {:fetch (fn [] (str (stateful)))
                                                             :cache-get (fn cache-get' [conn]
                                                                          (redis/execute conn [:hget "testing:2" "foo"]))
                                                             :cache-set (fn cache-set' [conn fetch-result]
                                                                          (redis/execute conn [:hset "testing:2" "foo" fetch-result]))})]
      ;; increments again because we're checking a different cache key!
      (is (= "6" (get-or-fetch)))
      (is (= "6" (get-or-fetch)))
      (is (= 7 (stateful)))
      (is (= "6" (get-or-fetch)))
      (redis/execute (tu/conn) [:del "testing:2"])
      (is (= "8" (get-or-fetch))))))
