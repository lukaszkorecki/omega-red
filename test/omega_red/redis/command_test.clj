(ns omega-red.redis.command-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.redis.command :as redis.cmd]))

(deftest key-prefixing-test
  (testing "no prefix - nothing happens"
    (is (= [:get "one"]
           (redis.cmd/process {}
                              [:get "one"]))))

  (testing "works with commands which do not accept keys"
    (is (= [:ping]
           (redis.cmd/process {:key-prefix "test"}
                              [:ping])))

    (is (= [:keys "foo*"]
           (redis.cmd/process {:key-prefix "lol"}
                              [:keys "foo*"]))))
  (testing "simple case"
    (is (= [:get "foo:one"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:get "one"])))))

(deftest key-prefixing-with-other-args-test
  (testing "simple case with extra args that are not keys"
    (is (= [:set "foo:one" "bar"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:set "one" "bar"])))

    (is (= [:setex "foo:one" 10 "val"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:setex "one" 10 "val"])))

    (is (= [:hmget "foo:one" "k1" "k2"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:hmget "one" "k1" "k2"])))))

(deftest multi-key-prefixing-test
  (testing "multi-key no extra args"
    (is (= [:exists "foo:one" "foo:two" "foo:three"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:exists "one" "two" "three"])))

    (is (= [:del "foo:one" "foo:two" "foo:three"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:del "one" "two" "three"]))))

  (testing "mulit key with extra arg"
    (is (= [:blpop "foo:one" 10]
           (redis.cmd/process {:key-prefix "foo"}
                              [:blpop "one" 10])))
    (is (= [:blpop "foo:one" "foo:two" "foo:three" 10]
           (redis.cmd/process {:key-prefix "foo"}
                              [:blpop "one" "two" "three" 10])))))

(deftest mget-test
  (testing "mget / mset"
    (is (= [:mget "foo:one" "foo:two" "foo:three"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:mget "one" "two" "three"])))

    (is (= [:mset
            "foo:one" "one-val"
            "foo:two" "two-val"
            "foo:three" "three-val"]
           (redis.cmd/process {:key-prefix "foo"}
                              [:mset
                               "one" "one-val"
                               "two" "two-val"
                               "three" "three-val"])))))

(deftest prefixing-formats-test
  (testing "prefix can be a keyword"
    (is (= [:get "foo:one"]
           (redis.cmd/process {:key-prefix :foo}
                              [:get "one"]))))

  (testing "prefix can be a namespaced keyword"
    (is (= [:get "omega-red.redis.command-test/foo.bar:one"]
           (redis.cmd/process {:key-prefix ::foo.bar}
                              [:get "one"]))))

  (testing "keys can be namespaced keywords"
    (is (= [:get "omega-red.redis.command-test/one"]
           (redis.cmd/process {}
                              [:get ::one])))))

(deftest sort-test
  (is (= [:sort "foo:one" "BY" "*"]
         (redis.cmd/process {:key-prefix "foo"} [:sort "one" :by "*"]))))

(deftest xreadgroup-test
  (testing "key prefixing is not supported!"
    (is (= [:xreadgroup
            "GROUP" "foo" "bar"
            "COUNT" 1
            "BLOCK" 100
            "STREAMS" "q1" "q2"]
           (redis.cmd/process {:key-prefix "foo"} [:xreadgroup
                                                   :group "foo" "bar"
                                                   :count 1
                                                   :block 100
                                                   :streams "q1" "q2"])))))

(deftest eval-test
  (testing "key prefixing is not supported either"
    (is (= [:eval "bar" 1 "return nil;"]
           (redis.cmd/process {:key-prefix "foo"} [:eval "bar" 1 "return nil;"])))))

(deftest token-in-command-test
  (testing "expiration in SET - using upper case keyword"
    (is (= [:set "foo" "bar" "EX" 10]
           (redis.cmd/process {} [:set "foo" "bar" :EX 10]))))

  (testing "sort with BY and DESC as lower case keywords"
    (is (= [:sort "foo" "BY" "*" "DESC"]
           (redis.cmd/process {} [:sort "foo" :by "*" :desc])))))
