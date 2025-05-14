(ns omega-red.gen-cmd-config-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [omega-red.gen-cmd-config :as gcc]))

(deftest get-spec-test
  (is (= {:arguments ["key"]
          :command :get
          :has-block-key-args? false
          :has-only-one-key-arg? true
          :is-key-first-arg? true
          :has-variadic-key-args? false
          :num-args 2
          :variadic? false}
         (get gcc/command-specs :get))))

(deftest set-spec-test
  (is (= {:arguments ["key" "value" "condition" "get" "expiration"]
          :command :set
          :has-block-key-args? false
          :has-only-one-key-arg? true
          :is-key-first-arg? true
          :has-variadic-key-args? false
          :num-args 3
          :variadic? true}
         (get gcc/command-specs :set))))

(deftest mset-spec-test
  (is (= {:arguments ["data"]
          :command :mset
          :has-block-key-args? true
          :has-only-one-key-arg? false
          :is-key-first-arg? false
          :has-variadic-key-args? false
          :num-args 3
          :variadic? true}
         (get gcc/command-specs :mset))))

(deftest mget-spec-test
  (is (= {:arguments ["key"]
          :command :mget
          :has-block-key-args? false
          :has-only-one-key-arg? false
          :is-key-first-arg? true
          :has-variadic-key-args? true
          :num-args 2
          :variadic? true}
         (get gcc/command-specs :mget))))

(deftest del-spec-test
  (is (= {:arguments ["key"]
          :command :del
          :has-block-key-args? false
          :has-only-one-key-arg? false
          :is-key-first-arg? true
          :has-variadic-key-args? true
          :num-args 2
          :variadic? true}
         (get gcc/command-specs :del))))

(deftest blpop-spec-test
  (is (= {:arguments ["key" "timeout"]
          :command :blpop
          :has-block-key-args? false
          :has-variadic-key-args? true
          :has-only-one-key-arg? false
          :is-key-first-arg? true
          :num-args 3
          :variadic? true}
         (get gcc/command-specs :blpop))))

(deftest hset-spec-test
  (is (= {:arguments ["key" "data"]
          :command :hset
          :has-block-key-args? false
          :has-only-one-key-arg? true
          :has-variadic-key-args? false
          :is-key-first-arg? true
          :num-args 4
          :variadic? true}
         (get gcc/command-specs :hset))))
