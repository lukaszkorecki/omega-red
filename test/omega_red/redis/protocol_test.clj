(ns omega-red.redis.protocol-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.redis.protocol :as redis.proto]))

(deftest key-prefixing-test
  (testing "no prefix - nothing happens"
    (is (= [:get "one"]
           (redis.proto/apply-key-prefixes {}
                                           [:get "one"]))))

  (testing "works with commands which do not accept keys"
    (is (= [:ping]
           (redis.proto/apply-key-prefixes {:key-prefix "test"}
                                           [:ping])))

    (is (= [:keys "foo*"]
           (redis.proto/apply-key-prefixes {:key-prefix "lol"}
                                           [:keys "foo*"]))))
  (testing "simple case"
    (is (= [:get "foo:one"]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:get "one"]))))

  (testing "simple case with extra args that are not keys"
    (is (= [:set "foo:one" "bar"]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:set "one" "bar"])))

    (is (= [:setex "foo:one" 10 "val"]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:setex "one" 10 "val"])))

    (is (= [:hmget "foo:one" "k1" "k2"]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:hmget "one" "k1" "k2"]))))

  (testing "multi-key no extra args"
    (is (= [:exists "foo:one" "foo:two" "foo:three"]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:exists "one" "two" "three"])))

    (is (= [:del "foo:one" "foo:two" "foo:three"]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:del "one" "two" "three"]))))

  (testing "mulit key with extra arg"
    (is (= [:blpop "foo:one" 10]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:blpop "one" 10])))
    (is (= [:blpop "foo:one" "foo:two" "foo:three" 10]
           (redis.proto/apply-key-prefixes {:key-prefix "foo"}
                                           [:blpop "one" "two" "three" 10]))))

  (testing "prefix can be a keyword"
    (is (= [:get "foo:one"]
           (redis.proto/apply-key-prefixes {:key-prefix :foo}
                                           [:get "one"]))))

  (testing "prefix can be a namespaced keyword"
    (is (= [:get "omega-red.redis.protocol-test/foo.bar:one"]
           (redis.proto/apply-key-prefixes {:key-prefix ::foo.bar}
                                           [:get "one"]))))

  (testing "keys can be namespaced keywords"
    (is (= [:get "omega-red.redis.protocol-test/one"]
           (redis.proto/apply-key-prefixes {}
                                           [:get ::one])))))
