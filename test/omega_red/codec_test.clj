(ns omega-red.codec-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.codec :as codec]))

(deftest key-serialization-test
  (testing "this will blow up"
    (is (thrown-with-msg? Throwable #"Assert failed" (codec/serialize-key [nil]))))

  (is (= "a-key" (codec/serialize-key ["a-key"])))

  (is (= "x:one" (codec/serialize-key [:x "one"])))
  (is (= "omega-red.codec-test/x:one:two" (codec/serialize-key [::x "one" "two"]))))

(deftest data-de-serialization-test
  (testing "round trip for Clojure data"
    (is (= #{:x}
           (-> ;; input to Jedis is always a string
            (codec/serialize #{:x})
               ;; this is because internally, Jedis always gives us byte arrays
            (String/.getBytes "UTF-8")
            codec/deserialize))))

  (testing "simple data types - preserves Jedis/Redis return value semantics (aka losing type info)"
    (is (= "1" (-> (codec/serialize 1)
                   codec/deserialize)))

    (is (= "true" (-> (codec/serialize true)
                      codec/deserialize))))

  (testing "special case: things like HSET or SET return a number"
    (is (= 1 (codec/deserialize 1)))))
