(ns clj-watson.cli-spec
  (:require
   [babashka.cli :as cli]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def valid-outputs [:json :edn :stdout :stdout-simple :sarif])
(def valid-database-stragies [:dependency-check :github-advisory])
(def validate-file-exists {:pred #(-> % io/file .exists)
                           :ex-msg (fn [_m] "Specified file not found")})

(def spec-scan-args
  {:deps-edn-path
   {:alias :p
    :ref "<file>"
    :coerce :string
    :validate validate-file-exists
    :require true
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
               "If not provided uses resources/dependency-check.properties")}

   :clj-watson-properties
   {:alias :w
    :ref "<file>"
    :coerce :string
    :validate validate-file-exists
    :desc (str "Path of an additional, optional properties file\n"
               "Overrides values in dependency-check.properties\n"
               "If not specified classpath is searched for cljwatson.properties")}})

(defn- kw->str
  "Copied from bb cli"
  [kw]
  (subs (str kw) 1))

(defn- cell-widths [rows]
  (reduce
    (fn [widths row]
      (map max (map count row) widths)) (repeat 0) rows))

(defn- pad-cells
  "Adapted from bb cli"
  [rows widths]
  (let [pad-row (fn [row]
                  (map (fn [width cell] (cli/pad width cell)) widths row))]
    (map pad-row rows)))

(defn- expand-multilines
  "Expand last column cell over multiple rows if it contains newlines"
  [rows]
  (reduce (fn [acc row]
            (let [[line & extra-lines] (-> row last str/split-lines)
                  cols (count row)]
              (if (seq extra-lines)
                (apply conj acc
                       (assoc (into [] row) (dec cols) line)
                       (map #(conj (into [] (repeat (dec cols) ""))
                                   %)
                            extra-lines))
                (conj acc row))))
          []
          rows))

(defn- format-table
  "Modified from bb cli format-table. Allow pre-computed widths to be passed in."
  [{:keys [rows indent widths] :or {indent 2}}]
  (let [widths (or widths (cell-widths rows))
        rows (expand-multilines rows)
        rows (pad-cells rows widths)
        fmt-row (fn [leader divider trailer row]
                  (str leader
                       (apply str (interpose divider row))
                       trailer))]
    (->> rows
         (map (fn [row]
                (fmt-row (apply str (repeat indent " ")) " " "" row)))
         (map str/trimr)
         (str/join "\n"))))

(defn- opts->table
  "Based on bb cli opts->table."
  [{:keys [spec order opts]}]
  (let [usage-help-style (:usage-help-style opts)]
    (mapv (fn [[long-opt {:keys [alias default default-desc ref desc extra-desc require]}]]
            (keep identity
                  [(if alias
                     (if (= :clojure-tool usage-help-style)
                       (str alias ",")
                       (str "-" (kw->str alias) ","))
                     "")
                   (if (= :clojure-tool usage-help-style)
                     (str long-opt " " ref)
                     (str "--" (kw->str long-opt) " " ref))
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
    (format-table {:rows (opts->table cfg) :indent 2})
    (let [groups (mapv #(assoc % :rows
                               (opts->table (assoc cfg :order (:order %))))
                       groups)
          widths (cell-widths (mapcat :rows groups))]
      (->> groups
           (reduce (fn [acc {:keys [heading rows]}]
                     (conj acc (str heading "\n"
                                    (format-table {:rows rows :widths widths}))))
                   [])
           (str/join "\n\n")))))

(defn- usage-help [{:keys [opts]}]
  (println "clj-watson")
  (println)
  (println "ARG USAGE:")
  (println " scan [options..]")
  (println)
  (println
   (format-opts {:spec spec-scan-args :opts opts
                 :groups [{:heading "OPTIONS:"
                           :order [:deps-edn-path :output :aliases :database-strategy :suggest-fix :fail-on-result :help]}
                          {:heading "OPTIONS valid when database-strategy is dependency-check:"
                           :order [:dependency-check-properties :clj-watson-properties]}]})))

(defn- error [text]
  (str "\u001B[31m* ERROR: " text "\u001B[0m"))

(defn- usage-error [{:keys [spec type cause msg option opts] :as data}]
  (case type
    :clj-watson/cli
    (println (error msg))

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
  (println)
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
        {:keys [args opts]} (cli/parse-args args {:spec (select-keys spec-scan-args [:help :usage-help-style]) })]
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
      (cli/parse-opts orig-args {:spec spec-scan-args :error-fn usage-error :restrict true}))))

(defn validate-tool-opts [opts]
  (->> opts
       opts->args
       (into ["scan" ":usage-help-style" ":clojure-tool"])
       parse-args))
