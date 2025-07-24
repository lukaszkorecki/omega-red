(ns omega-red.gen-cmd-config
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :as walk])
  (:import
   [java.io File]))

(let [source-url "https://raw.githubusercontent.com/taoensso/carmine/refs/heads/master/resources/redis-commands.json"
      local-fname "./redis-commands.json"
      f (io/file local-fname)]
  (when-not (File/.exists f)
    (->> (slurp source-url)
         (spit local-fname))))

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

(defn extract-all-tokens [arguments]
  (let [tokens (transient [])]
    (walk/postwalk (fn [x]
                     (if (and (map? x) (not-empty (:token x)))
                       (conj! tokens (:token x))
                       x))
                   arguments)

    (persistent! tokens)))

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
                    {:keys [arguments arity]} spec
                    ;; helper values to help reason about the command and its spec
                    variadic? (neg? arity)
                    num-args (abs arity) ;; includes command itself!
                    command-tokens (extract-all-tokens arguments)

                    ;; key stuff
                    has-key-args? (->> arguments
                                       (filter (fn [arg] (= "key" (:type arg))))
                                       not-empty?)

                    ;; technically only MSET and MSETNX have a block key arg
                    has-block-key-args? (= "key" (->> arguments
                                                      first
                                                      :arguments
                                                      first
                                                      :type))

                    arg-names (if has-block-key-args?
                                (concat (->> arguments
                                             (remove #(contains? #{"data" "block"} (:type %)))
                                             (map :name))
                                        (->> arguments
                                             (mapcat #(map :name (:arguments %)))
                                             (filter not-empty)))
                                (map :name arguments))

                    ;; vast majority of commands have a single key arg, even if they have multiple or block args
                    is-key-first-arg? (boolean
                                       (or has-block-key-args?
                                           (= "key" (:type (first arguments)))))
                    ;; NOTE: this is not precise - there are commands with multiple key args AFTER some other arguments
                    ;;       we don't care about those for now
                    has-variadic-key-args? (boolean
                                            (and is-key-first-arg?
                                                 (:multiple (first arguments))))
                    has-only-one-key-arg? (and is-key-first-arg? (not has-variadic-key-args?))]

                (when (or has-key-args? has-block-key-args? (seq command-tokens))
                  [cmd-kw {:command cmd-kw
                           :process-tokens? (boolean (seq command-tokens))
                           :arguments arg-names
                           :tokens command-tokens

                           :num-args num-args
                           :variadic? variadic?

                           :process-keys? (or has-key-args? has-block-key-args?)
                           :is-key-first-arg? is-key-first-arg?
                           :has-only-one-key-arg? has-only-one-key-arg?
                           :has-variadic-key-args? has-variadic-key-args?
                           :has-block-key-args? has-block-key-args?}]))))

       (remove nil?)
       (map (fn [[cmd spec]]
              {cmd spec}))
       (into {})))

(defn -main [& _]
  (spit "resources/redis-commands.edn" (pr-str command-specs))
  command-specs)
