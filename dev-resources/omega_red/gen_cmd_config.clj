(ns omega-red.gen-cmd-config
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]))

(def raw-redis-cmds (-> "https://raw.githubusercontent.com/taoensso/carmine/refs/heads/master/resources/redis-commands.json"
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

(def redis-key-search-config
  (->> raw-redis-cmds
       (mapv (fn [[cmd cmd-spec]]
               (let [{:strs [summary key_specs arguments arity]} cmd-spec
                     all-args (map #(get % "name") arguments)
                     key-arg (first arguments)
                     is-single-key-arg? (and (= "key" (get key-arg "type")) (not (get key-arg "multiple")))
                     ]
                 (when key_specs
                   {:cmd cmd
                    :summary summary
                    :all-args all-args
                    :arity arity
                    :is-single-key-arg? is-single-key-arg?
                    :key-search {:start-idx (get-in key_specs [0 "begin_search" "spec" "index"])
                                 :last-key-idx (get-in key_specs [0 "find_keys" "spec" "lastkey"])
                                 :step (get-in key_specs [0 "find_keys" "spec" "keystep"])
                                 :limit (get-in key_specs [0 "find_keys" "spec" "limit"])}}))))

       (remove nil?)
       (map (fn [{:keys [cmd] :as cmd-config}]
              (hash-map cmd cmd-config)))

       (into {})))

(defn -main [& _]
  (spit "resources/redis-cmd-key-config.edn"
        (pr-str redis-cmds-config))

  (spit "resources/redis-cmd-config.edn"
        (pr-str redis-key-search-config)))
