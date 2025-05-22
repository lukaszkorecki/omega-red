(ns omega-red.redis-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [omega-red.redis :as redis]
   [omega-red.test-util :as tu]))

(use-fixtures :each (fn [test]
                      (tu/with-test-system (fn []
                                             (tu/clean-up-all-data (tu/conn))
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

(deftest sets-test
  (testing "we can work with sets"
    (is (= 3
           (redis/execute (tu/conn) [:sadd "test.some.set" :x :y :z :x])))

    (is (= 3
           (redis/execute (tu/conn) [:scard "test.some.set"])))

    (is (= #{:x :y :z}
           (set (redis/execute (tu/conn) [:spop "test.some.set" 3]))))))

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
    (is (= 1 (redis/execute (tu/conn) [:del "test.some.key"]))))

  (testing "pipelines work too"
    (is (= 0 (redis/execute (tu/conn) [:exists "test.some.key.pipe-hash"])))

    (is (= [nil
            2
            1
            1
            [{:foo :x} #{1 2 3} {::bananas ['(1) '(2)]} "1"]]
           (redis/execute-pipeline (tu/conn)
                                   [[:get "test.some.key.pipe-hash"]
                                    [:hset "test.some.key.pipe-hash" "one" {:foo :x} "two" #{1 2 3}]
                                    [:hset "test.some.key.pipe-hash" "three" {::bananas ['(1) '(2)]}]
                                    [:hset "test.some.key.pipe-hash" "four" 1]
                                    [:hmget "test.some.key.pipe-hash" "one" "two" "three" "four"]])))))

(deftest inspecting-test
  (is (= {:connected? :omega-red.client/unkown
          :instance-addr (-> tu/redis-config :instance-addr)}
         (dissoc (tu/conn) :pool)))

  ;; NOTE: this doesn't work for some reason
  #_(is (satisfies? omega-red.redis/IRedis (tu/conn))))

(deftest mset-mget-prefix-test
  (is (= "OK"
         (redis/execute (tu/prefixed-conn) [:mset "test.some.key" "foo" "test.some.key2" "bar"])))

  (is (= {"test.some.key" "foo"
          "test.some.key2" "bar"}
         (zipmap ["test.some.key" "test.some.key2"]
                 (redis/execute (tu/prefixed-conn)
                                [:mget "test.some.key" "test.some.key2"])))))

(deftest key-prefixer-fn-test
  (testing "simple"
    (is (= "foo" (redis/key "foo")))
    (is (= "foo:bar" (redis/key "foo" "bar"))))

  (testing "keywords"
    (is (= "foo:bar:baz" (redis/key :foo :bar :baz)))
    (is (= "omega-red.redis-test/foo:omega-red.redis-test/bar:omega-red.redis-test/baz"
           (redis/key ::foo ::bar ::baz))))

  (testing "nil handling"
    (is (= "foo" (redis/key nil "foo")))
    (is (= "foo:bar:baz" (redis/key :foo "bar" nil :baz))))

  (testing "validation"
    (is (thrown-with-msg? AssertionError #"Assert failed:"
                          (redis/key :foo :bar :baz 10)))))
