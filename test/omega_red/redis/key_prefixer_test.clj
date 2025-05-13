(ns omega-red.redis.key-prefixer-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.redis.command :as redis.kp]))

(deftest key-prefixing-test
  (testing "no prefix - nothing happens"
    (is (= [:get "one"]
           (redis.kp/apply-prefix nil
                                  [:get "one"]))))

  (testing "works with commands which do not accept keys"
    (is (= [:ping]
           (redis.kp/apply-prefix "test"
                                  [:ping])))

    (is (= [:keys "foo*"]
           (redis.kp/apply-prefix "lol"
                                  [:keys "foo*"]))))
  (testing "simple case"
    (is (= [:get "foo:one"]
           (redis.kp/apply-prefix "foo"
                                  [:get "one"]))))

  (testing "simple case with extra args that are not keys"
    (is (= [:set "foo:one" "bar"]
           (redis.kp/apply-prefix "foo"
                                  [:set "one" "bar"])))

    (is (= [:setex "foo:one" 10 "val"]
           (redis.kp/apply-prefix "foo"
                                  [:setex "one" 10 "val"])))

    (is (= [:hmget "foo:one" "k1" "k2"]
           (redis.kp/apply-prefix "foo"
                                  [:hmget "one" "k1" "k2"]))))

  (testing "multi-key no extra args"
    (is (= [:exists "foo:one" "foo:two" "foo:three"]
           (redis.kp/apply-prefix "foo"
                                  [:exists "one" "two" "three"])))

    (is (= [:del "foo:one" "foo:two" "foo:three"]
           (redis.kp/apply-prefix "foo"
                                  [:del "one" "two" "three"]))))

  (testing "mulit key with extra arg"
    (is (= [:blpop "foo:one" 10]
           (redis.kp/apply-prefix "foo"
                                  [:blpop "one" 10])))
    (is (= [:blpop "foo:one" "foo:two" "foo:three" 10]
           (redis.kp/apply-prefix "foo"
                                  [:blpop "one" "two" "three" 10]))))

  (testing "mget / mset"
    (is (= [:mget "foo:one" "foo:two" "foo:three"]
           (redis.kp/apply-prefix "foo"
                                  [:mget "one" "two" "three"])))

    (is (= [:mset
            "foo:one" "v1"
            "foo:two" "V2"
            "foo:three" "v2"]
           (redis.kp/apply-prefix "foo"
                                  [:mset
                                   "one" "v1"
                                   "two" "v2"
                                   "three" "v3"]))))

  (testing "prefix can be a keyword"
    (is (= [:get "foo:one"]
           (redis.kp/apply-prefix :foo
                                  [:get "one"]))))

  (testing "prefix can be a namespaced keyword"
    (is (= [:get "omega-red.redis.key-prefixer-test/foo.bar:one"]
           (redis.kp/apply-prefix ::foo.bar
                                  [:get "one"]))))

  (testing "keys can be namespaced keywords"
    (is (= [:get "omega-red.redis.key-prefixer-test/one"]
           (redis.kp/apply-prefix nil [:get ::one])))))
