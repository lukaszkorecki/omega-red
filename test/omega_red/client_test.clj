(ns omega-red.client-test
  (:require
   [omega-red.test-util :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [omega-red.redis :as redis]
   [omega-red.redis.protocol :as redis.proto]))

(use-fixtures :each tu/with-test-system)

(deftest component-with-prefix-test
  (testing "basic get set del"
    ;; NOTE this connection is configured with a prefix in test-util
    (is (= 0 (redis/execute (:redis-prefixed @tu/sys) [:exists "pref-key"])))
    (is (= "OK" (redis/execute (:redis-prefixed @tu/sys) [:set "pref-key" "foo"])))

    (testing "keys used are actually prefixed"
      ;; NOTE: this uses connection pool directly to bypass prefixing:
      (is (= ["test-prefix:pref-key"]
             (redis.proto/execute* (:pool (:redis-prefixed @tu/sys)) [:keys "test-prefix*"]))))

    (is (= 1 (redis/execute (:redis-prefixed @tu/sys) [:exists "pref-key"])))

    (testing "connection without prefix configured will not see the data"
      (is (= 0 (redis/execute (tu/conn) [:exists "pref-key"]))))

    (is (= "foo" (redis/execute (:redis-prefixed @tu/sys) [:get "pref-key"])))
    (is (= 1 (redis/execute (:redis-prefixed @tu/sys) [:del "pref-key"])))

    (testing "no data with prefix"
      (is (= []
             (redis.proto/execute* (:pool (:redis-prefixed @tu/sys)) [:keys "test-prefix*"]))))))

(deftest building-keys
  (testing "keys don't always have to be strings, and work with prefixes"

    (is (= "OK" (redis/execute (:redis-prefixed @tu/sys) [:set (redis/key "pref-key" "bananas") "foo"])))

    (is (= ["test-prefix:pref-key:bananas"]
           (redis.proto/execute* (:pool (:redis-prefixed @tu/sys)) [:keys "test-prefix*"])))))
