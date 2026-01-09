(ns omega-red.mock-lock-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.lock :as lock]))

(deftest create-mock-test
  (testing "mock lock always acquires when always-acquire? is true (default)"
    (let [mock-lock (lock/create-mock {})]
      (is (true? (lock/acquire mock-lock)))
      (is (true? (lock/acquire-with-timeout mock-lock)))
      (is (true? (lock/acquire-with-timeout mock-lock {})))
      (is (true? (lock/is-lock-holder? mock-lock)))))

  (testing "mock lock never acquires when always-acquire? is false"
    (let [mock-lock (lock/create-mock {:always-acquire? false})]
      (is (false? (lock/acquire mock-lock)))
      (is (false? (lock/acquire-with-timeout mock-lock)))
      (is (false? (lock/acquire-with-timeout mock-lock {})))
      (is (false? (lock/is-lock-holder? mock-lock)))))

  (testing "mock lock always renews"
    (let [mock-lock (lock/create-mock {})]
      (is (true? (lock/renew mock-lock)))))

  (testing "mock lock always releases"
    (let [mock-lock (lock/create-mock {})]
      (is (true? (lock/release mock-lock)))))

  (testing "mock lock returns a fixed ID"
    (let [mock-lock (lock/create-mock {})]
      (is (= "mock-lock-id" (lock/get-id mock-lock)))
      (is (= "mock-lock-id" (lock/get-lock-holder-id mock-lock)))))

  (testing "mock lock returns 0 for expiry"
    (let [mock-lock (lock/create-mock {})]
      (is (= 0 (lock/lock-expiry-in-ms mock-lock))))))
