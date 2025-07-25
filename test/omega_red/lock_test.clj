(ns omega-red.lock-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [omega-red.test-util :as tu]
            [omega-red.redis :as redis]
            [omega-red.client :as redis.client]
            [omega-red.lock :as redis-lock]
            [com.stuartsierra.component :as component]))

(def locking-component-map
  {:conn-1 (redis.client/create (merge tu/redis-config {:key-prefix "locking-test"}))
   :conn-2 (redis.client/create (merge tu/redis-config {:key-prefix "locking-test"}))
   :conn-3 (redis.client/create (merge tu/redis-config {:key-prefix "locking-test"}))
   :lock-1 (component/using
            (redis-lock/create {:lock-key "my-cool-op"
                                :expiry-ms 2000
                                :acquire-timeout-ms 500})
            {:conn :conn-1})

   :lock-2 (component/using
            (redis-lock/create {:lock-key "my-cool-op"
                                :expiry-ms 2000
                                :acquire-timeout-ms 500})
            {:conn :conn-2})

   :lock-3 (component/using
            (redis-lock/create {:lock-key "my-cool-op"
                                :expiry-ms 2000
                                :acquire-timeout-ms 500})
            {:conn :conn-3})})

(use-fixtures :each (fn [test]
                      (tu/with-test-system test
                        {:extra-components locking-component-map})))

(deftest simple-acquire-test
  (testing "sequential acquire + release"
    (is (false? (redis-lock/is-lock-holder? (:lock-1 @tu/sys))))
    (is (true? (redis-lock/acquire (:lock-1 @tu/sys))))

    (testing "lock data is in Redis"
      (is (= ["locking-test:my-cool-op"]
             (redis/execute (:conn-1 @tu/sys) [:keys "*"]))))

    (is (true? (redis-lock/is-lock-holder? (:lock-1 @tu/sys))))

    (testing "nobody else can acquire"
      (is (false? (redis-lock/acquire (:lock-2 @tu/sys))))
      (is (false? (redis-lock/acquire (:lock-3 @tu/sys)))))

    (testing "release + steal"
      (is (true? (redis-lock/release (:lock-1 @tu/sys))))
      (is (true? (redis-lock/acquire (:lock-2 @tu/sys)))))))

(deftest custom-acquire-timeout-test
  (is (true? (redis-lock/acquire (:lock-1 @tu/sys))))
  (let [run-time-ms (tu/make-timer)]

    (testing "default timeout"
      (is (false? (redis-lock/acquire-with-timeout (:lock-2 @tu/sys))))

      (is (<= 300 (run-time-ms) 500)
          "acquire should take less than 500ms but more than 300 since there's a lock held already"))

    (testing "we can customize lock acquisition timeout overriding instance setting"
      (is (false? (redis-lock/acquire-with-timeout (:lock-2 @tu/sys)
                                                   {:acquire-timeout-ms 1000})))

      (is (<= 500 (run-time-ms) 1500)
          "acquire should take more than 500ms, but less than 1000ms, since we set the timeout to 1000ms and we already tried once with a shorter timeout"))))

(deftest acquire-renew-release-test
  (testing "sequential acquire + release"
    (is (true? (redis-lock/acquire-with-timeout (:lock-1 @tu/sys))))
    (is (<= 0 (redis-lock/lock-expiry-in-ms (:lock-1 @tu/sys)) 2000))
    (testing "sequential but with some concurrency"
      (is (false? @(future (redis-lock/acquire-with-timeout (:lock-2 @tu/sys)))))
      (is (false? @(future (redis-lock/acquire-with-timeout (:lock-3 @tu/sys))))))

    (is (= (redis-lock/get-id (:lock-1 @tu/sys))
           (redis-lock/get-lock-holder-id (:lock-1 @tu/sys)))))

  (testing "just before we expire the lock, we renew it"
    (is (<= (redis-lock/lock-expiry-in-ms (:lock-1 @tu/sys)) 1500))
    (Thread/sleep 1000)
    (is (true? (redis-lock/is-lock-holder? (:lock-1 @tu/sys)))) ;; not expired yet

    (is (true? (redis-lock/renew (:lock-1 @tu/sys))))
    (is (<= 0 (redis-lock/lock-expiry-in-ms (:lock-1 @tu/sys)) 2000))
    (is (true? (redis-lock/is-lock-holder? (:lock-1 @tu/sys))))

    (testing "still can't be acquired"
      (is (false? (redis-lock/acquire-with-timeout (:lock-2 @tu/sys))))
      (is (false? (redis-lock/acquire-with-timeout (:lock-3 @tu/sys))))))

  (testing "we let the lease expire"
    (Thread/sleep 2000)
    (is (false? (redis-lock/is-lock-holder? (:lock-1 @tu/sys))))

    (testing "lock can be acquired again"
      (is (true? (redis-lock/acquire-with-timeout (:lock-2 @tu/sys))))

      (is (= (redis-lock/get-lock-holder-id (:lock-1 @tu/sys))
             (redis-lock/get-id (:lock-2 @tu/sys)))))

    (testing "lock can't be acquired since its held"
      (is (false? (redis-lock/acquire-with-timeout (:lock-1 @tu/sys))))
      (is (true? (redis-lock/acquire-with-timeout (:lock-2 @tu/sys))))

      (testing "We let the lock expire"
        (Thread/sleep 2200)
        (is (false? (redis-lock/is-lock-holder? (:lock-2 @tu/sys))))

        (is (nil? (redis-lock/get-lock-holder-id (:lock-2 @tu/sys))))))))

(deftest concurrent-locking-test
  (let [do-work (fn [lock-component-key-name]
                  [lock-component-key-name
                   (when (redis-lock/acquire (get @tu/sys lock-component-key-name))
                     (Thread/sleep 1000)
                     (redis-lock/release (get @tu/sys lock-component-key-name)))])
        try-concurrent-acquire (fn [] (doall
                                       (pmap do-work [:lock-1 :lock-2 :lock-3])))]

    (testing "only one connection acquires the lock"
      (testing "attempt 1"
        (is (= 1 (count (filter #(true? (second %)) (try-concurrent-acquire))))))

      (testing "attempt 2"
        (is (= 1 (count (filter #(true? (second %)) (try-concurrent-acquire))))))

      (testing "attempt 3"
        (is (= 1 (count (filter #(true? (second %)) (try-concurrent-acquire)))))))

    (testing "we let the lock expire and try again"
      (Thread/sleep 2000)
      (testing "final attempt"
        (is (= 1 (count (filter #(true? (second %)) (try-concurrent-acquire)))))))))

(deftest with-lock-macro-test
  (let [do-work (fn [lock-component-key-name]
                  (redis-lock/with-lock (get @tu/sys lock-component-key-name)
                    (Thread/sleep 1000)
                    "we're done"))]
    (is (= [{:status :omega-red.lock/acquired-and-released :result "we're done"}
            {:status :omega-red.lock/not-acquired}
            {:status :omega-red.lock/not-acquired}]
           (->> [:lock-1 :lock-2 :lock-3]
                (pmap do-work)
                doall

                (sort-by :status))))))
