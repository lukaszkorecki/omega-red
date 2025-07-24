(ns omega-red.client.connection-pool-test
  (:require [clojure.test :refer [deftest testing is]]
            [omega-red.client.connection-pool :as conn-pool]))

(deftest creating-conn-pool-config-test
  (testing "'max-total' is required"
    (is (thrown? clojure.lang.ExceptionInfo
                 (bean (conn-pool/configure {:max-wait-millis 100})))))

  (testing "'min-idle' and 'max-idle' are optional and can be calculated"
    (is (= {:minIdle 10
            :maxIdle 50
            :maxTotal 100
            :maxWaitMillis -1}
           (-> (conn-pool/configure {:max-total 100})
               (bean)
               (select-keys [:minIdle :maxIdle :maxTotal :maxWaitMillis])))))

  (testing "all options"
    (is (= {:minIdle 2
            :maxIdle 6
            :maxTotal 100
            :maxWaitMillis 100}
           (-> (conn-pool/configure {:max-total 100
                                     :min-idle 2
                                     :max-idle 6
                                     :max-wait-millis 100})
               (bean)
               (select-keys [:minIdle :maxIdle :maxTotal :maxWaitMillis])))))

  (testing "weird options"
    (is (= {:minIdle 1
            :maxIdle 1
            :maxTotal 1
            :maxWaitMillis -1}
           (-> (conn-pool/configure {:max-total 1})
               (bean)
               (select-keys [:minIdle :maxIdle :maxTotal :maxWaitMillis]))))))
