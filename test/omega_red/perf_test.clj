;; Various performance tests to ensure connection pool and protocol usage doesn't break under heavy load
;; or with resource constraints

(ns omega-red.perf-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.stuartsierra.component :as component]
   [omega-red.client :as redis.client]
   [omega-red.redis :as redis]
   [omega-red.test-util :as tu])
  (:import
   [java.util.concurrent ExecutorService Executors TimeUnit]))

(set! *warn-on-reflection* true)

(use-fixtures :each tu/with-test-system)

(defn exec-stop-and-wait [^ExecutorService executor num-s]
  (.shutdown executor)
  (.awaitTermination executor num-s TimeUnit/SECONDS))

(deftest regular-command-stress-test
  (testing "everything works as expected with small conn pool and many individual commands"
    (let [client (component/start (redis.client/create (assoc tu/redis-config :connection-pool {:max-total 4
                                                                                                :min-idle 1
                                                                                                :max-idle 2})))
          executor (Executors/newFixedThreadPool 10)]
      (try
        (let [tasks (->> (range 0 1000)
                         (mapv (fn [i]
                                 (.submit executor ^Callable
                                          (fn []
                                            (Thread/sleep ^long (rand-int 30))
                                            (redis/execute client [:set (str "test." i) {:number i}]))))))
              result (mapv deref tasks)]
          (is (= 1000 (count result)))
          (is (= #{"OK"} (set result)))
          (is (= (set (range 0 1000))
                 (set (mapv :number (mapv #(redis/execute client [:get (str "test." %)]) (range 0 1000)))))))
        (finally
          (exec-stop-and-wait executor 5)
          (component/stop client))))))

(deftest pipelined-command-stress-test
  (testing "everything works as expected with small conn pool and many pipelined commands"
    (let [client (component/start (redis.client/create (assoc tu/redis-config :connection-pool {:max-total 4
                                                                                                :min-idle 1
                                                                                                :max-idle 2})))
          executor (Executors/newFixedThreadPool 10)]
      (try
        (let [tasks (->> (range 0 1000)
                         (filter even?)
                         (mapv (fn [i]
                                 (.submit executor
                                          ^Callable (fn []
                                                      (Thread/sleep ^long (rand-int 30))
                                                      (redis/execute-pipeline client
                                                                              [[:set (str "test." i) {:number i}]
                                                                               [:set (str "test." (inc i)) {:number (inc i)}]]))))))
              result (mapv deref tasks)]
          (is (= 500 (count result)))
          (is (= #{["OK" "OK"]} (set result)))
          (is (= (set (range 0 1000))
                 (set (mapv :number (mapv #(redis/execute client [:get (str "test." %)]) (range 0 1000)))))))
        (finally
          (exec-stop-and-wait executor 5)
          (component/stop client))))))
