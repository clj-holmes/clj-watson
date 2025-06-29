(ns clj-watson.cli-spec
  (:require
   [babashka.cli :as cli]
   [clj-watson.logic.table :as table]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def valid-outputs [:json :edn :stdout :stdout-simple :sarif])
(def valid-database-stragies [:dependency-check :github-advisory])
(def validate-file-exists {:pred #(-> % io/file .exists)
                           :ex-msg (fn [_m] "Specified file not found")})

(declare styled-long-opt)

(def spec-scan-args
  {:deps-edn-path
   {:alias :p
    :ref "<file>"
    :coerce :string
    :validate validate-file-exists
    :require :yes ;; Normally would `:require true` but clj-holmes\clj-holmes thinks this is a clojure spec
                  ;; and raises: "Typo on schema declaration using :require instead of :required."
    :desc "Path of deps.edn file to scan"}

   :output
   {:alias :o
    :ref (format "<%s>" (str/join "|" (mapv name valid-outputs)))
    :coerce :keyword
    :validate #((set valid-outputs) %)
    :default :stdout
    :default-desc "stdout"
    :desc "Output type for vulnerability findings"}

   :aliases
   {:alias :a
    :coerce [:string] ;; would coerce to keyword here, but would prefer to distinguish '*' as something special
    :desc "Include deps.edn aliases in analysis, specify '*' for all."
    :extra-desc {:clojure-tool "For multiple, use a vector, ex: '[alias1 alias2]'"
                 :cli "For multiple, repeat arg, ex: -a alias1 -a alias2"}}

   :database-strategy
   {:alias :t
    :ref (format "<%s>" (str/join "|" (mapv name valid-database-stragies)))
    :coerce :keyword
    :validate #((set valid-database-stragies) %)
    :default :dependency-check
    :default-desc "dependency-check"
    :desc "Vulnerability database strategy"}

   :suggest-fix
   {:alias :s
    :coerce :boolean
    :default false
    :desc "Include dependency remediation suggestions in vulnurability findings"}

   :fail-on-result
   {:alias :f
    :coerce :boolean
    :default false
    :desc (str "When enabled, exit with non-zero on any vulnerability findings\n"
               "Useful for CI/CD")}

   :cvss-fail-threshold
   {:alias :c
    :ref "<score>"
    :coerce :double
    :desc (str "Exit with non-zero when any vulnerability's CVSS base score is >= threshold\n"
               "CVSS scores range from 0.0 (least severe) to 10.0 (most severe)\n"
               "We interpret a score of 0.0 as suspicious\n"
               "Missing or suspicious CVSS base scores are conservatively derived\n"
               "Useful for CI/CD")}

   :usage-help-style
   {:coerce :keyword
    :default :cli
    :desc "Internal opt to control style of usage help"}

   :help
   {:alias :h
    :coerce :boolean
    :desc "Show usage help"}

   ;; dependency-check strategy specific args follow
   :dependency-check-properties
   {:alias :d
    :ref "<file>"
    :coerce :string
    :validate validate-file-exists
    :desc (str "Path of a dependency-check properties file\n"
               "If not provided uses resources/dependency-check.properties")
    :deprecated-fn (fn [m] (format "Please use %s instead." (styled-long-opt :clj-watson-properties m)))}

   :clj-watson-properties
   {:alias :w
    :ref "<file>"
    :coerce :string
    :validate validate-file-exists
    :desc (str "Path of an additional, optional properties file\n"
               "Overrides values in dependency-check.properties\n"
               "If not specified classpath is searched for clj-watson.properties")}

   :run-without-nvd-api-key
   {:type :flag
    :default false
    :desc (str "Run without an nvd.api.key configured.\n"
               "It will be slow and we cannot recommend it.\n"
               "See docs for configuration.")}})

(defn- kw->str
  "Copied from bb cli"
  [kw]
  (subs (str kw) 1))

(defn styled-long-opt [longopt {:keys [usage-help-style]}]
  (if (= :clojure-tool usage-help-style)
    longopt
    (str "--" (kw->str longopt))))

(defn styled-alias [alias {:keys [usage-help-style]}]
  (if (= :clojure-tool usage-help-style)
    alias
    (str "-" (kw->str alias))))

(defn- opts->table
  "Based on bb cli opts->table."
  [{:keys [spec order opts]}]
  (let [usage-help-style (:usage-help-style opts)]
    (mapv (fn [[long-opt {:keys [alias default default-desc ref desc extra-desc require]}]]
            (keep identity
                  [(if alias
                     (str (styled-alias alias opts) ",")
                     "")
                   (str (styled-long-opt long-opt opts) " " ref)
                   (->> [(if-let [attribute (or (when require "*required*")
                                                default-desc
                                                (when (some? default) (str default)))]
                           (format "%s [%s]" desc attribute)
                           desc)
                         (get extra-desc usage-help-style)]
                        (keep identity)
                        (str/join "\n"))]))
          (if (map? spec)
            (let [order (or order (keys spec))]
              (map (fn [k] [k (spec k)]) order))
            spec))))

(defn- format-opts
  "Group-aware version of bb cli format-opts.
  Optionally specifiy `:groups [{:heading \"My heading1\" :order [:arg1 :arg2]}]` "
  [{:as cfg
    :keys [groups]}]
  (if (not groups)
    (table/format-table {:rows (opts->table cfg) :indent 2})
    (let [groups (mapv #(assoc % :rows
                               (opts->table (assoc cfg :order (:order %))))
                       groups)
          widths (table/cell-widths (mapcat :rows groups))]
      (->> groups
           (reduce (fn [acc {:keys [heading rows]}]
                     (conj acc (str heading "\n"
                                    (table/format-table {:rows rows :widths widths}))))
                   [])
           (str/join "\n\n")))))

(defn- error [text]
  (str "\u001B[31m* ERROR: " text "\u001B[0m"))

(defn- warning [text]
  (str "\u001B[33m* WARNING: " text "\u001B[0m"))

(defn- report-deprecations [opts]
  (doseq [deprecated-opt [:dependency-check-properties]]
    (when (deprecated-opt opts)
      (println (warning
                (format "%s, %s is deprecated and will be deleted in a future release. %s"
                        (styled-alias (-> spec-scan-args deprecated-opt :alias) opts)
                        (styled-long-opt deprecated-opt opts)
                        ((-> spec-scan-args deprecated-opt :deprecated-fn) opts)))))))

(defn- usage-help [{:keys [opts]}]
  (println "clj-watson")
  (println)
  (println "ARG USAGE:")
  (println " scan [options..]")
  (println)
  (println
   (format-opts {:spec spec-scan-args :opts opts
                 :groups [{:heading "OPTIONS:"
                           :order [:deps-edn-path :output :aliases :database-strategy :suggest-fix :fail-on-result :cvss-fail-threshold :help]}
                          {:heading "OPTIONS valid when database-strategy is dependency-check:"
                           :order [:clj-watson-properties :run-without-nvd-api-key]}]})))

(defn- usage-error [{:keys [spec type cause msg option opts] :as data}]
  (report-deprecations opts)
  (case type
    :clj-watson/cli
    (println (str (error msg) "\n"))

    :org.babashka/cli
    (let [error-desc (cause {:require "Missing required argument"
                             :validate "Invalid value for argument"
                             :coerce "Cannot coerce value for argument"
                             :restrict "Unrecognized argument"})
          ;; show our custom validation messages
          msg (when (and (= :validate cause)
                         (some-> spec option :validate :ex-msg))
                msg)]
      (if (= :restrict cause)
        (println (error (format "%s: %s" error-desc option)))
        (let [arg-desc (format-opts {:spec (select-keys spec [option])
                                     :opts opts})]
          (println (error (format "%s:%s\n%s" error-desc
                                  (if msg (str " " msg) "")
                                  arg-desc))))))

    (throw (ex-info msg data)))
  (usage-help data)
  (System/exit 1))

(defn- opts->args [m]
  (->> m
       (reduce (fn [acc [k v]]
                 (if (vector? v)
                   (apply conj acc (interleave (repeat k) v))
                   (conj acc k v)))
               [])
       (mapv pr-str)))

(defn parse-args [args]
  ;; can entertain moving to bb cli dispatch when we have more than one command
  (let [orig-args args
        {:keys [args opts]} (cli/parse-args args {:spec (select-keys spec-scan-args [:help :usage-help-style])})]
    (cond
      (:help opts)
      (do
        (usage-help {:opts opts})
        (System/exit 0))

      (not= ["scan"] args)
      (usage-error {:type :clj-watson/cli
                    :msg (format "Invalid command, the only valid command is scan, detected: %s" (str/join ", " args))
                    :spec spec-scan-args
                    :opts opts})

      :else
      (let [opts (cli/parse-opts orig-args {:spec spec-scan-args :error-fn usage-error :restrict true})]
        (if (and (:cvss-fail-threshold opts) (:fail-on-result opts))
          (usage-error {:type :clj-watson/cli
                        :msg (format "Invalid usage, specify only one of: %s"
                                     (->> [:fail-on-result :cvss-fail-threshold]
                                          (mapv #(styled-long-opt % opts))
                                          (str/join ", ")))
                        :spec spec-scan-args
                        :opts opts})
          (report-deprecations opts))
        opts))))

(defn validate-tool-opts [opts]
  (->> opts
       opts->args
       (into ["scan" ":usage-help-style" ":clojure-tool"])
       parse-args))
