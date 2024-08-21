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
   [clojure.java.io :as io]
   [clojure.tools.reader.edn :as edn]))

(defmulti scan* (fn [{:keys [database-strategy]}] database-strategy))

(defmethod scan* :github-advisory [{:keys [deps-edn-path suggest-fix aliases]}]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        repositories (select-keys deps [:mvn/repos])
        config (when-let [config-file (io/resource "clj-watson-config.edn")]
                 (edn/read-string (slurp config-file)))
        allow-list (adapter.config/config->allow-config-map config)
        vulnerable-dependencies (controller.gh.vulnerability/scan-dependencies dependencies repositories allow-list)]
    (if suggest-fix
      (controller.remediate/scan vulnerable-dependencies deps)
      vulnerable-dependencies)))

(defmethod scan* :dependency-check [{:keys [deps-edn-path suggest-fix aliases
                                            dependency-check-properties clj-watson-properties] :as opts}]
  ;; dependency-check uses Apache Commons JCS, ask it to use log4j2 to allow us to configure its noisy logging
  (System/setProperty "jcs.logSystem" "log4j2")
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        repositories (select-keys deps [:mvn/repos])
        scanned-dependencies (controller.dc.scanner/start! dependencies
                                                           dependency-check-properties
                                                           clj-watson-properties
                                                           opts)
        vulnerable-dependencies (controller.dc.vulnerability/extract scanned-dependencies dependencies repositories)]
    (if suggest-fix
      (controller.remediate/scan vulnerable-dependencies deps)
      vulnerable-dependencies)))

(defmethod scan* :default [opts]
  (scan* (assoc opts :database-strategy "dependency-check")))

(defn- contains-vulnerabilities?
  "Checks whether there are any reported vulnerabilities with a CVSS score above the specified threshold."
  [vulnerabilities fail-on-cvss]
  (->> vulnerabilities
       (map :vulnerabilities)
       (mapcat #(map (comp :score :cvss :advisory) %))
       (filter #(< fail-on-cvss %))
       (seq)))

(defn do-scan
  "Indirect entry point for -M usage."
  [opts]
  (logging-config/init)
  (let [{:keys [fail-on-cvss output deps-edn-path]} opts
        vulnerabilities (scan* opts)]
    (controller.output/generate vulnerabilities deps-edn-path output)
    (if (contains-vulnerabilities? vulnerabilities fail-on-cvss)
      (System/exit 1)
      (System/exit 0))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn scan
  "Direct entrypoint for -X & -T usage."
  [opts]
  (do-scan (cli-spec/validate-tool-opts opts)))

(comment
  (def vulnerabilities (scan* {:deps-edn-path     "resources/vulnerable-deps.edn"
                               :database-strategy "dependency-check"
                               :suggest-fix       true}))

  (def vulnerabilities (scan* {:deps-edn-path     "resources/vulnerable-deps.edn"
                               :database-strategy "github-advisory"}))
  (controller.output/generate vulnerabilities "deps.edn" "sarif")
  (controller.output/generate vulnerabilities "deps.edn" "stdout-simple"))
