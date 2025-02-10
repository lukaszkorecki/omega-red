(ns omega-red.redis-test
  (:require
   [omega-red.test-util :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [omega-red.redis :as redis]))

(use-fixtures :each (fn [test]
                      (tu/with-test-system (fn []
                                             (test)))))

(deftest basic-ops-test
  (testing "basic get set del"
    (is (= 0 (redis/execute (tu/conn) [:exists "test.some.key"])))
    (is (= "OK" (redis/execute (tu/conn) [:set "test.some.key" "foo"])))
    (is (= 1 (redis/execute (tu/conn) [:exists "test.some.key"])))
    (is (= "foo" (redis/execute (tu/conn) [:get "test.some.key"])))
    (is (= 1 (redis/execute (tu/conn) [:del "test.some.key"])))))

(deftest pipelne-test
  (testing "operations can be pipelined - kinda like transaction"
    (is (= 0 (redis/execute (tu/conn) [:exists "test.some.key.pipe"])))
    (is (= [nil "OK" "oh ok" 1]
           (redis/execute-pipeline (tu/conn)
                                   [[:get "test.some.key.pipe"]
                                    [:set "test.some.key.pipe" "oh ok"]
                                    [:get "test.some.key.pipe"]
                                    [:del "test.some.key.pipe"]])))
    (testing "once pipeline finishes value is unchanged"
      (is (= 0 (redis/execute (tu/conn) [:exists "test.some.key.pipe"]))))))

(deftest clj-data-test
  (testing "get set del with a clojure map"
    (is (= 0 (redis/execute (tu/conn) [:exists "test.some.key"])))
    (is (= "OK" (redis/execute (tu/conn) [:set "test.some.key" {:foo 1}])))
    (is (= 1 (redis/execute (tu/conn) [:exists "test.some.key"])))
    (is (= {:foo 1} (redis/execute (tu/conn) [:get "test.some.key"])))
    (is (= 1 (redis/execute (tu/conn) [:del "test.some.key"]))))

  (testing "get set del with a clojure set"
    (is (= 0 (redis/execute (tu/conn) [:exists "test.some.key"])))
    (is (= "OK" (redis/execute (tu/conn) [:set "test.some.key" #{{:bar 1} {:foo 1}}])))
    (is (= 1 (redis/execute (tu/conn) [:exists "test.some.key"])))
    (is (= #{{:bar 1} {:foo 1}} (redis/execute (tu/conn) [:get "test.some.key"])))
    (is (= 1 (redis/execute (tu/conn) [:del "test.some.key"])))))

(deftest key-prefixing-test
  (testing "no prefix - nothing happens"
    (is (= [:get "one"]
           (redis/apply-key-prefixes {}
                                     [:get "one"]))))

  (testing "works with commands which do not accept keys"
    (is (= [:ping]
           (redis/apply-key-prefixes {:key-prefix "test"}
                                     [:ping])))

    (is (= [:keys "foo*"]
           (redis/apply-key-prefixes {:key-prefix "lol"}
                                     [:keys "foo*"]))))
  (testing "simple case"
    (is (= [:get "foo:one"]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:get "one"]))))

  (testing "simple case with extra args that are not keys"
    (is (= [:set "foo:one" "bar"]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:set "one" "bar"])))

    (is (= [:setex "foo:one" 10 "val"]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:setex "one" 10 "val"])))

    (is (= [:hmget "foo:one" "k1" "k2"]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:hmget "one" "k1" "k2"]))))

  (testing "multi-key no extra args"
    (is (= [:exists "foo:one" "foo:two" "foo:three"]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:exists "one" "two" "three"])))

    (is (= [:del "foo:one" "foo:two" "foo:three"]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:del "one" "two" "three"]))))

  (testing "mulit key with extra arg"
    (is (= [:blpop "foo:one" 10]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:blpop "one" 10])))
    (is (= [:blpop "foo:one" "foo:two" "foo:three" 10]
           (redis/apply-key-prefixes {:key-prefix "foo"}
                                     [:blpop "one" "two" "three" 10]))))

  (testing "prefix can be a keyword"
    (is (= [:get "foo:one"]
           (redis/apply-key-prefixes {:key-prefix :foo}
                                     [:get "one"]))))

  (testing "prefix can be a namespaced keyword"
    (is (= [:get "omega-red.redis-test/foo.bar:one"]
           (redis/apply-key-prefixes {:key-prefix ::foo.bar}
                                     [:get "one"])))))

(deftest component-with-prefix-test
  (testing "basic get set del"
    ;; NOTE this connection is configured with a prefix in test-util
    (is (= 0 (redis/execute (:redis-prefixed @tu/sys) [:exists "pref-key"])))
    (is (= "OK" (redis/execute (:redis-prefixed @tu/sys) [:set "pref-key" "foo"])))

    (testing "keys used are actually prefixed"
      (is (= ["test-prefix:pref-key"]
             (redis/execute! (:redis-prefixed @tu/sys) [:keys "test-prefix*"]))))

    (is (= 1 (redis/execute (:redis-prefixed @tu/sys) [:exists "pref-key"])))

    (testing "connection without prefix configured will not see the data"
      (is (= 0 (redis/execute (tu/conn) [:exists "pref-key"]))))

    (is (= "foo" (redis/execute (:redis-prefixed @tu/sys) [:get "pref-key"])))
    (is (= 1 (redis/execute (:redis-prefixed @tu/sys) [:del "pref-key"])))

    (testing "no data with prefix"
      (is (= []
             (redis/execute! (:redis-prefixed @tu/sys) [:keys "test-prefix*"]))))))
