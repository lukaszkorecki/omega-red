(ns omega-red.gen-cmd-config
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [taoensso.carmine :as carmine]))

(def raw-redis-cmds (-> "redis-commands.json" ;; is bundled with Carmine!
                        io/resource
                        slurp
                        (json/parse-string false)
                        (update-keys #(-> % str/lower-case (str/replace " " "-") keyword))))

(def redis-cmds-config
  (->>
   raw-redis-cmds
   (map (fn [[cmd cmd-spec]]
              ;; find all commands where 'key' is the first argument
          (let [key-arg (get-in cmd-spec ["arguments" 0])]
            (when (= "key" (get key-arg "type"))
              (let [{:strs [summary multiple]} key-arg
                    all-args (map #(get % "name") (get cmd-spec "arguments"))]
                {:cmd cmd
                 :summary summary
                 :type (if multiple :multi :single)
                 :non-key-args-tail-count (count (rest all-args))
                 :all-args all-args})))))
   (remove nil?)

   (map (fn [{:keys [cmd] :as cmd-config}]
          (hash-map cmd cmd-config)))

   (into {})))

(defn -main [& _]
  (spit "resources/redis-cmd-key-config.edn"
        (pr-str redis-cmds-config)))
