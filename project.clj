(defproject org.clojars.lukaszkorecki/omega-red "2.5.0-SNAPSHOT"
  :description "Redis client for Cloure, based on Jedis, with optional Component support"
  :url "https://github.com/nomnom-insights/nomnom.omega-red"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2018
            :key "mit"}

  :deploy-repositories {"clojars" {:sign-releases false
                                   :username :env/clojars_username
                                   :password :env/clojars_password}}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.stuartsierra/component "1.1.0"]
                 [redis.clients/jedis "6.0.0"]
                 ;; for (de)serializing Clojure data transparently
                 [com.cognitect/transit-clj "1.0.333"]]

  :global-vars {*warn-on-reflection* true}
  ;; include whole stack traces
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]


  :profiles {:dev {:dependencies [[org.slf4j/slf4j-api "2.0.17"]
                                  [org.clojure/tools.logging "1.3.0"]
                                  [ch.qos.logback/logback-classic "1.5.18"]
                                  [cheshire "6.0.0"]
                                  [lambdaisland/kaocha "1.91.1392"]]
                   :extra-paths ["dev-resources" "script"]}}

  :aliases {"ci-test" ["with-profile" "+dev" "run" "-m" "kaocha.runner"]})
