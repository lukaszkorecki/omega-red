(ns omega-red.redis.key-prefixer
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [omega-red.codec :as codec]))

;; run in repl to generate this config
(comment
  (require '[omega-red.gen-cmd-config :as gen-cmd-config])
  (gen-cmd-config/-main))

(def cmd-config
  (-> "redis-cmd-config.edn"
      io/resource
      slurp
      edn/read-string))

(defn- make-prefixer [key-prefix]
  (fn with-prefix'
    [a-key]
    (codec/serialize-key [key-prefix a-key])))

(defn find-keys-and-apply-prefix [{:keys [key-search prefixer cmd+args]}]
  (let [{:keys [start-idx step last-key-idx limit]} key-search

        key-indexes (set (range start-idx (- (dec (count cmd+args)) last-key-idx) step))
        ]


    (r/pp {:cmd+args (map-indexed #(vector % %2) cmd+args)
           :key-indexes (sort key-indexes)})
    (->> cmd+args
         (map-indexed (fn [idx arg]
                        (if (key-indexes idx)
                          (prefixer arg)
                          arg)))
         vec)))

(defn apply-prefix [key-prefix cmd+args]
  (if-let [cmd-conf (get cmd-config (first cmd+args))]
    (let [prefixer (make-prefixer key-prefix)
          {:keys [is-single-key-arg? key-search]} cmd-conf]

      (if is-single-key-arg?
        ;; optimized for single key arg, which is 154 out of 193 known commands
        (update-in cmd+args [1] prefixer)
        ;; slow case - we're dealing with multi key ops
        (find-keys-and-apply-prefix {:key-search key-search
                                     :prefixer prefixer
                                     :cmd+args cmd+args})))
    ;; no command config, skip
    cmd+args))
