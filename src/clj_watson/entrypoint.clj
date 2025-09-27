(ns clj-watson.entrypoint
  (:require
   [clj-watson.adapter.config :as adapter.config]
   [clj-watson.cli-spec :as cli-spec]
   [clj-watson.controller.dependency-check.scanner :as controller.dc.scanner]
   [clj-watson.controller.dependency-check.vulnerability :as controller.dc.vulnerability]
   [clj-watson.controller.deps :as controller.deps]
   [clj-watson.controller.github.vulnerability :as controller.gh.vulnerability]
   [clj-watson.controller.output :as controller.output]
   [clj-watson.controller.remediate :as controller.remediate]
   [clj-watson.logging-config :as logging-config]
   [clj-watson.logic.summarize :as summarize]
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]))

(defmulti scan* (fn [{:keys [database-strategy]}] database-strategy))
(defmethod scan* :github-advisory [{:keys [dependencies repositories]}]
  (let [config (when-let [config-file (io/resource "clj-watson-config.edn")]
                 (edn/read-string (slurp config-file)))
        allow-list (adapter.config/config->allow-config-map config)]
    (controller.gh.vulnerability/scan-dependencies dependencies repositories allow-list)))

(defmethod scan* :dependency-check [{:keys [dependency-check-properties clj-watson-properties
                                            dependencies repositories] :as opts}]
  (let [{:keys [findings exit] :as result}
        (controller.dc.scanner/start! dependencies
                                      dependency-check-properties
                                      clj-watson-properties
                                      opts)]
    (if exit
      result
      (assoc result :findings (controller.dc.vulnerability/extract findings dependencies repositories)))))

(defmethod scan* :default [opts]
  (scan* (assoc opts :database-strategy :dependency-check)))

(defn do-scan [{:keys [fail-on-result cvss-fail-threshold output deps-edn-path aliases suggest-fix] :as opts}]
  (logging-config/init)
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        repositories (select-keys deps [:mvn/repos])
        {:keys [findings exit] :as result} (scan* (assoc opts
                                                         :dependencies dependencies
                                                         :repositories repositories))]
    (if exit
      result
      (let [findings (if suggest-fix
                       (controller.remediate/scan findings deps)
                       findings)]
        (controller.output/generate findings deps-edn-path output)
        (-> result summarize/final-summary controller.output/final-summary)
        (cond
          (and fail-on-result (seq findings))
          {:exit 1 :exit-error "fail-on-result requested and met"}

          cvss-fail-threshold
          (let [{:keys [scores-met] :as cvss-summary} (summarize/cvss-threshold-summary cvss-fail-threshold result)]
            (controller.output/cvss-threshold-summary cvss-summary)
            (when (seq scores-met)
              {:exit 1 :exit-error "cvss-fail-threshold requested and met"})))))))

(defn scan-main [args]
  (let [opts (cli-spec/parse-args args)]
    (if (:exit opts)
      opts
      (do-scan opts))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn scan
  "Direct entrypoint for -X & -T usage."
  [opts]
  (reduce (fn [opts f]
            (let [{:keys [exit exit-error] :as res} (f opts)]
              (cond (nil? exit) res
                    (zero? exit) (reduced res)
                    :else (throw (ex-info exit-error {})))))
          opts
          [cli-spec/validate-tool-opts do-scan]))

(comment
  (def vulnerabilities (do-scan {:deps-edn-path     "resources/vulnerable-deps.edn"
                                 :database-strategy :dependency-check
                                 :suggest-fix       true}))

  (def vulnerabilities (scan* {:deps-edn-path     "resources/vulnerable-deps.edn"
                               :database-strategy :github-advisory}))

  (controller.output/generate vulnerabilities "deps.edn" "sarif")
  (controller.output/generate vulnerabilities "deps.edn" "stdout-simple")

  :eoc)
