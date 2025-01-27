(defproject org.clojars.lukaszkorecki/omega-red "2.0.0"
  :description "Component firendly Redis client, based on Carmine"
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
                 [com.taoensso/carmine "3.4.1"]]
  :global-vars {*warn-on-reflection* true}

  :profiles {:dev {:dependencies [[org.clojure/tools.logging "1.3.0"]
                                  [ch.qos.logback/logback-classic "1.5.15"]
                                  [cheshire "5.13.0"]]
                   :extra-paths ["dev-resources" "script"]}})
