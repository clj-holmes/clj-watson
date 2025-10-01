(ns clj-watson.unit.cli-spec-test
  (:require
   [clj-watson.cli-spec :as cli-spec]
   [clj-watson.test-util :as tu]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(defn- main-parse-args [& args]
  (tu/with-out-capture
    (apply cli-spec/parse-args args)))

(defn- exec-parse-opts [opts]
  (tu/with-out-capture
    (cli-spec/validate-tool-opts opts)))

(defn- main-usage [pre-lines]
  (m/embeds (into pre-lines ["clj-watson"
                             "ARG USAGE:"
                             "OPTIONS:"
                             #".* -p, --deps-edn-path"])))

(defn- exec-usage [pre-lines]
  (m/embeds (into pre-lines ["clj-watson"
                             "ARG USAGE:"
                             "OPTIONS:"
                             #".* :p, :deps-edn-path"])))

;; main only, clojure exec reports on missing values
(deftest main-missing-value
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (main-usage [#"\* ERROR: Option specified without value:"
                                       #".* -o, --output"])}
              (main-parse-args ["scan" "--output"]))))

(deftest main-help-shows-usage
  (doseq [args [["--help"] ["-h"] ["scan" "--help"] ["scan" "-h"]]]
    (is (match? {:result {:exit 0 :exit-error m/absent}
                 :out-lines (main-usage [])}
                (main-parse-args args))
        args)))

(deftest exec-help-shows-usage
  (doseq [opts [{:h true} {:help true}]]
    (is (match? {:result {:exit 0 :exit-error m/absent}
                 :out-lines (exec-usage [])}
                (exec-parse-opts opts))
        opts)))

(deftest main-scan-command-is-required
  (doseq [args [[] ["badcommand"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: Invalid command.*"])}
                (main-parse-args args))
        args)))

(deftest main-deps-edn-path-is-required
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (main-usage [#"\* ERROR: .*Missing required"
                                       #".* -p, --deps-edn-path"])}
              (main-parse-args ["scan"]))))

(deftest exec-deps-edn-path-is-required
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (exec-usage [#"\* ERROR: .*Missing required"
                                       #".* :p, :deps-edn-path"])}
              (exec-parse-opts {}))))

(deftest main-deps-edn-path-must-exist
  (doseq [args
          [["scan" "-p" "idontexist.edn"]
           ["scan" "--deps-edn-path" "idontexist.edn"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: .*file not found"
                                         #".* -p, --deps-edn-path"])}
                (main-parse-args args))
        args)))

(deftest exec-deps-edn-path-must-exist
  (doseq [opts
          [{:p "idontexist.edn"}
           {:deps-edn-path "idontexist.edn"}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: .*file not found"
                                         #".* :p, :deps-edn-path"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-depency-check-properties-must-exist
  (doseq [args
          [["scan" "-p" "deps.edn" "-d" "idontexist.properties"]
           ["scan" "-p" "deps.edn" "--dependency-check-properties" "idontexist.properties"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: .*file not found"
                                         #".* -d, --dependency-check-properties"])}
                (main-parse-args args))
        args)))

(deftest exec-dependency-check-properties-must-exist
  (doseq [opts
          [{:p "deps.edn" :d "idontexist.properties"}
           {:deps-edn-path "deps.edn" :dependency-check-properties "idontexist.properties"}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: .*file not found"
                                         #".* :d, :dependency-check-properties"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-clj-watson-properties-must-exist
  (doseq [args
          [["scan" "-p" "deps.edn" "-d" "idontexist.properties"]
           ["scan" "-p" "deps.edn" "--dependency-check-properties" "idontexist.properties"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: .*file not found"
                                         #".* -w, --clj-watson-properties"])}
                (main-parse-args args))
        args)))

(deftest exec-clj-watson-properties-must-exist
  (doseq [opts
          [{:p "deps.edn" :w "idontexist.properties"}
           {:deps-edn-path "deps.edn" :clj-watson-properties "idontexist.properties"}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: .*file not found"
                                         #".* :w, :clj-watson-properties"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-args-must-be-recognized
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (main-usage [#"\* ERROR: Unrecognized option: --unrecognized-arg"])}
              (main-parse-args ["scan" "--unrecognized-arg"])))
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (main-usage [#"\* ERROR: Unrecognized option: -x"])}
              (main-parse-args ["scan" "-x"]))))

(deftest exec-args-must-be-recognized
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (exec-usage [#"\* ERROR: Unrecognized option: :unrecognized-arg"])}
              (exec-parse-opts {:unrecognized-arg "some-val"})))
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (exec-usage [#"\* ERROR: Unrecognized option: :x"])}
              (exec-parse-opts {:x "some-val"}))))

(deftest main-output-format-must-be-valid
  (doseq [args
          [["scan" "-p" "deps.edn" "-o" "bad"]
           ["scan" "-p" "deps.edn" "--output" "bad"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: .*Invalid value"
                                         #".* -o, --output"])}
                (main-parse-args args))
        args)))

(deftest exec-output-format-must-be-valid
  (doseq [opts
          [{:p "deps.edn" :o "bad"}
           {:p "deps.edn" :output "bad"}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: .*Invalid value"
                                         #".* :o, :output"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-database-strategy-must-be-valid
  (doseq [args
          [["scan" "-p" "deps.edn" "-t" "bad"]
           ["scan" "-p" "deps.edn" "--database-strategy" "bad"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: .*Invalid value"
                                         #".* -t, --database-strategy"])}
                (main-parse-args args))
        args)))

(deftest exec-database-strategy-must-be-valid
  (doseq [opts
          [{:p "deps.edn" :t "bad"}
           {:p "deps.edn" :database-strategy "bad"}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: .*Invalid value"
                                         #".* :t, :database-strategy"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-dependency-check-properties-shows-deprecated-warning
  (doseq [args
          [["scan" "-p" "deps.edn" "-d" "resources/dependency-check.properties"]
           ["scan" "-p" "deps.edn" "--dependency-check-properties" "resources/dependency-check.properties"]]]
    (is (match? {:result {:exit m/absent :exit-error m/absent}
                 :out-lines (m/embeds [#"\* WARNING: -d, --dependency-check-properties is deprecated"])}
                (main-parse-args args))
        args)))

(deftest exec-dependency-check-properties-shows-deprecated-warning
  (doseq [opts
          [{:p "deps.edn" :d "resources/dependency-check.properties"}
           {:p "deps.edn" :dependency-check-properties "resources/dependency-check.properties"}]]
    (is (match? {:result {:exit m/absent :exit-error m/absent}
                 :out-lines (m/embeds [#"\* WARNING: :d, :dependency-check-properties is deprecated"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-only-specify-one-fail-option
  (doseq [args
          [["scan" "-p" "deps.edn" "--fail-on-result" "--cvss-fail-threshold" "3.2"]
           ["scan" "-p" "deps.edn" "-f" "-c" "3.2"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: .* only one of: --fail-on-result, --cvss-fail-threshold"])}
                (main-parse-args args))
        args)))

(deftest exec-only-specify-one-fail-option
  (doseq [opts
          [{:p "deps.edn" :fail-on-result true :cvss-fail-threshold 3.2}
           {:p "deps.edn" :f true :c 3.2}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: Invalid usage, specify only one of: :fail-on-result, :cvss-fail-threshold"])}
                (exec-parse-opts opts))
        opts)))

(deftest main-unable-to-parse
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (main-usage [#"\* ERROR: Unable to parse, found invalid: NO"])}
              (main-parse-args ["scan" "--suggest-fix" "NO"]))))

(deftest exec-unable-to-parse
  (is (match? {:result {:exit 1 :exit-error "usage error"}
               :out-lines (exec-usage [#"\* ERROR: Unable to parse, found invalid: NO"])}
              (exec-parse-opts {:suggest-fix "NO"}))))

(deftest exec-coerces-options
  ;; A usability issue with -X and -T is that they do limited coercion
  ;; with the help of bb cli, we help out with this
  (doseq [opts
          [{:p 'deps.edn  :o :edn  :a ['alias1 'alias2]   :t :dependency-check  :s true   :c 1}
           {:p "deps.edn" :o "edn" :a ["alias1" "alias2"] :t "dependency-check" :s "true" :c "1"}]]
    (is (match? {:result {:exit m/absent
                          :exit-error m/absent
                          :deps-edn-path "deps.edn"
                          :output :edn
                          :aliases ["alias1" "alias2"]
                          :database-strategy :dependency-check
                          :suggest-fix true
                          :cvss-fail-threshold 1.0}
                 :out-lines [""]}
                (exec-parse-opts opts))
        opts)))

(deftest main-defaults-applied
  (is (match? {:result (m/equals {:exit m/absent
                                  :exit-error m/absent
                                  :deps-edn-path "deps.edn"
                                  :output :stdout
                                  :suggest-fix false
                                  :database-strategy :dependency-check
                                  :fail-on-result false
                                  :run-without-nvd-api-key false
                                  :usage-help-style :cli})
               :out-lines [""]}
              (main-parse-args ["scan" "-p" "deps.edn"]))))

(deftest exec-defaults-applied
  (is (match? {:result (m/equals {:exit m/absent
                                  :exit-error m/absent
                                  :deps-edn-path "deps.edn"
                                  :output :stdout
                                  :suggest-fix false
                                  :database-strategy :dependency-check
                                  :fail-on-result false
                                  :run-without-nvd-api-key false
                                  :usage-help-style :clojure-tool})
               :out-lines [""]}
              (exec-parse-opts {:p "deps.edn"}))))

(deftest main-warn-for-ignored-opts
  (is (match? {:result {:exit m/absent
                        :exit-error m/absent}
               :out-lines (m/embeds [#"\* WARNING: -d, --dependency-check-properties ignored, it only applies when --database-strategy is dependency-check"
                                     #"\* WARNING: -w, --clj-watson-properties ignored, it only applies when --database-strategy is dependency-check"
                                     #"\* WARNING:     --run-without-nvd-api-key ignored, it only applies when --database-strategy is dependency-check"])}
              (main-parse-args ["scan" "-p" "deps.edn"
                                "-t" "github-advisory"
                                "--dependency-check-properties" "resources/dependency-check.properties"
                                "--clj-watson-properties" "resources/dependency-check.properties"
                                "--run-without-nvd-api-key"]))))

(deftest exec-warn-for-ignored-opts
  (is (match? {:result {:exit m/absent
                        :exit-error m/absent}
               :out-lines (m/embeds [#"\* WARNING: :d, :dependency-check-properties ignored, it only applies when :database-strategy is dependency-check"
                                     #"\* WARNING: :w, :clj-watson-properties ignored, it only applies when :database-strategy is dependency-check"
                                     #"\* WARNING:     :run-without-nvd-api-key ignored, it only applies when :database-strategy is dependency-check"])}
              (exec-parse-opts {:p "deps.edn"
                                :t "github-advisory"
                                :dependency-check-properties "resources/dependency-check.properties"
                                :clj-watson-properties "resources/dependency-check.properties"
                                :run-without-nvd-api-key true}))))

(deftest main-fails-on-impossible-coercion
  (doseq [args
          [["scan" "-p" "deps.edn" "--cvss-fail-threshold" "notanum"]
           ["scan" "-p" "deps.edn" "-c" "notanum"]]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (main-usage [#"\* ERROR: Cannot coerce notanum to double"
                                         #".* -c, --cvss-fail-threshold"])}
                (main-parse-args args))
        args)))

(deftest exec-fails-on-impossible-coercion
  (doseq [opts
          [{:p "deps.edn" :cvss-fail-threshold "notanum"}
           {:p "deps.edn" :c "notanum"}]]
    (is (match? {:result {:exit 1 :exit-error "usage error"}
                 :out-lines (exec-usage [#"\* ERROR: Cannot coerce notanum to double"
                                         #".* :c, :cvss-fail-threshold"])}
                (exec-parse-opts opts))
        opts)))
