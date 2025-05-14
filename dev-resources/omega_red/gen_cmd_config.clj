(ns omega-red.gen-cmd-config
  (:require
   [clojure.walk :as walk]
   [cheshire.core :as json]
   [clojure.string :as str]))

(comment
  (spit "./redis-commands.json" (slurp "https://raw.githubusercontent.com/taoensso/carmine/refs/heads/master/resources/redis-commands.json")))

;; map of {<CMD> {CMD SPEC}}
(def raw-redis-cmds
  (->> (slurp "./redis-commands.json")
       (json/parse-string)
       (walk/postwalk (fn [x]
                        (if (map? x)
                          (update-keys x keyword)
                          x)))

       (map (fn [[k v]]
              {(name k) v}))
       (into {})))

;; theres hundreds of commands, so let's first determine which have key args?
;; 154 commands have key as their first and only key-arg
;; 14 commands where 1st arg is key, but it's multiple keys after the command:
;; NOTE: there's a timeout in BL* commands as last arg
;; ("BLPOP" "WATCH" "BRPOP" "SINTER" "BZPOPMAX" "PFCOUNT" "EXISTS" "DEL" "MGET" "TOUCH" "UNLINK" "SDIFF" "BZPOPMIN" "SUNION")
;; 8 commands with 'block 'argument type
;; but not all have 'key' arg in them
;; ("CLUSTER ADDSLOTSRANGE" "CLUSTER DELSLOTSRANGE" "MSET" "XREADGROUP" "MSETNX" "HELLO" "CONFIG SET" "FAILOVER")
;; at this point we only really care about MSET and MSETNX so we do special handling
;; because of weird interleaved format
;; XXX: this is still far from perfect, look into how documentation for Redis is generated and translate
;;      that code into the command processor to account for command keywords like EX or LEFT/RIGHT

(def not-empty? (complement empty?))

;; NOTE this is largely unfinished, keep tweaking until we can figure out a 100% reliable way
;;      of finding keys and 'true' keywords (in Redis' protocol sense)
(def command-specs
  (->> raw-redis-cmds
       (map (fn [[cmd-str spec]]
              (let [cmd-kw (-> cmd-str str/lower-case (str/replace " " ".") keyword)
                    {:keys [arguments summary arity key_specs]} spec
                    ;; helper values to help reason about the command and its spec
                    variadic? (neg? arity)
                    num-args (abs arity) ;; includes command itself!
                    has-key-args? (->> arguments
                                       (filter (fn [arg] (= "key" (:type arg))))
                                       not-empty?)

                    has-block-key-args? (when-not has-key-args?
                                          (->> arguments
                                               (filter (fn [arg]
                                                         (when (= :mset cmd-kw)
                                                           (tap> [(= "block" (:type arg))
                                                                  (some #(= "key" (:type %)) (:arguments arg))
                                                                  (:arguments arg)]))

                                                         (and (= "block" (:type arg))
                                                              (some #(= "key" (:type %)) (:arguments arg)))))
                                               not-empty?))
                    ;; vast majority of commands have a single key arg
                    is-key-first-arg? (= "key" (:type (first arguments)))
                    ;; NOTE: this is not precise - there are commands with multiple key args AFTER some other arguments
                    ;;       we don't care about those for now
                    has-variadic-key-args? (and is-key-first-arg?
                                                (:multiple (first arguments)))]

                ;; TODO: extract all pure-tokens and store as 'keywords' so that we can
                ;;       allow for using Clojure keywords as Redis keywords eg
                ;;       [:set "foobar" "val" :EX 10]
                (when (or has-key-args? has-block-key-args?)
                  [cmd-kw (merge {:command cmd-kw
                                  :arguments (map :name arguments)
                                  :num-args num-args}
                                 (update-vals {:variadic? variadic?
                                               :is-key-first-arg? is-key-first-arg?
                                               :has-only-one-key-arg? (and is-key-first-arg? (not has-variadic-key-args?))
                                               :has-variadic-key-args? has-variadic-key-args?
                                               :has-block-key-args? has-block-key-args?}

                                              boolean))]))))
       (remove nil?)
       (map (fn [[cmd spec]]
              {cmd spec}))
       (into {})))

(defn -main [& _]
  (spit "resources/redis-commands.edn" (pr-str command-specs))
  command-specs)
