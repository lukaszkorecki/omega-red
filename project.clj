(defproject org.clojars.lukaszkorecki/omega-red "2.3.1-SNAPSHOT"
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

                 [redis.clients/jedis "5.2.0"]
                 ;; for (de)serializing Clojure data transparently
                 [com.cognitect/transit-clj "1.0.333"]]

  :global-vars {*warn-on-reflection* true}

  :profiles {:dev {:dependencies [[org.slf4j/slf4j-api "2.0.9"]
                                  [org.clojure/tools.logging "1.3.0"]
                                  [ch.qos.logback/logback-classic "1.5.17"]
                                  [com.stuartsierra/component "1.1.0"]
                                  [cheshire "5.13.0"]]
                   :extra-paths ["dev-resources" "script"]}})
