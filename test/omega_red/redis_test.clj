(ns omega-red.redis-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.stuartsierra.component :as component]
   [omega-red.client :as redis.client]
   [omega-red.redis :as redis]
   [omega-red.test-util :as tu])
  (:import
   [java.util.concurrent Executors]))

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

(deftest a-stress-test
  (let [client (component/start (redis.client/create (assoc tu/redis-config :connection-pool {:max-total 4
                                                                                              :min-idle 1
                                                                                              :max-idle 10})))
        executor (Executors/newFixedThreadPool 10)]
    (try
      (let [result (->> (range 0 1000)
                        (mapv (fn [i]
                                (.submit executor ^Callable (fn []
                                                              (Thread/sleep (rand-int 10))
                                                              (redis/execute client [:set (str "test." i) {:number i}])))))
                        (mapv deref))]

        (.awaitTermination executor 10 java.util.concurrent.TimeUnit/SECONDS)
        (is (= 1000 (count result)))
        (is (= #{"OK"} (set result)))
        (is (= (set (range 0 1000))
               (set (mapv :number (mapv #(redis/execute client [:get (str "test." %)]) (range 0 1000)))))))

      (finally
        (component/stop client)))))
