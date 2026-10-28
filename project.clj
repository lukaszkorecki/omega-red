(defproject org.clojars.lukaszkorecki/omega-red "3.0.2"
  :description "Redis client for Cloure, based on Jedis, with optional Component support"
  :url "https://github.com/lukaszkorecki/omega-red"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2018
            :key "mit"}

  :deploy-repositories {"clojars" {:sign-releases false
                                   :username :env/clojars_username
                                   :password :env/clojars_password}}

  :dependencies [[org.clojure/clojure "1.12.6"]
                 [com.stuartsierra/component "1.2.0"]
                 [redis.clients/jedis "8.0.2"]
                 ;; for (de)serializing Clojure data transparently
                 [com.cognitect/transit-clj "1.1.363"]]

  :global-vars {*warn-on-reflection* true}
  ;; include whole stack traces
  :jvm-opts ["-XX:-OmitStackTraceInFastThrow" "-Dclojure.main.report=stderr"]


  :profiles {:dev {:dependencies [[org.slf4j/slf4j-api "2.0.20"]
                                  [org.clojure/tools.logging "1.3.1"]
                                  [ch.qos.logback/logback-classic "1.6.6"]
                                  [cheshire "6.2.0"]
                                  [borkdude/jet "0.7.27"]
                                  [lambdaisland/kaocha "1.91.1392"]]
                   :extra-paths ["dev-resources" "script"]}}

  :aliases {"ci-test" ["with-profile" "+dev" "run" "-m" "kaocha.runner"]
            "gen-cmd-config" ["run" "-m" "omega-red.gen-cmd-config"]})
