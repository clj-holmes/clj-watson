(ns clj-watson.entrypoint
  (:require
   [clj-watson.controller.dependency-check.scanner :as controller.dc.scanner]
   [clj-watson.controller.dependency-check.vulnerability :as controller.dc.vulnerability]
   [clj-watson.controller.deps :as controller.deps]
   [clj-watson.controller.github.vulnerability :as controller.gh.vulnerability]
   [clj-watson.controller.output :as controller.output]
   [clj-watson.controller.remediate :as controller.remediate]))

(defmulti scan* (fn [{:keys [database-strategy]}] (keyword database-strategy)))

(defmethod scan* :github-advisory [{:keys [deps-edn-path suggest-fix aliases]}]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        repositories (select-keys deps [:mvn/repos])
        vulnerable-dependencies (controller.gh.vulnerability/scan-dependencies dependencies repositories)]
    (if suggest-fix
      (controller.remediate/scan vulnerable-dependencies deps)
      vulnerable-dependencies)))

(defmethod scan* :dependency-check [{:keys [deps-edn-path suggest-fix aliases dependency-check-properties]}]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        repositories (select-keys deps [:mvn/repos])
        scanned-dependencies (controller.dc.scanner/start! dependencies dependency-check-properties)
        vulnerable-dependencies (controller.dc.vulnerability/extract scanned-dependencies dependencies repositories)]
    (if suggest-fix
      (controller.remediate/scan vulnerable-dependencies deps)
      vulnerable-dependencies)))

(defmethod scan* :default [opts]
  (scan* (assoc opts :database-strategy "dependency-check")))

(defn scan [{:keys [fail-on-result output deps-edn-path] :as opts}]
  (let [vulnerabilities (scan* opts)]
    (controller.output/generate vulnerabilities deps-edn-path output)
    (if (and (-> vulnerabilities count (> 0)) fail-on-result)
      (System/exit 1)
      (System/exit 0))))

(comment
  (def vulnerabilities (scan* {:deps-edn-path "resources/vulnerable-deps.edn"
                               :database-strategy "dependency-check"
                               :suggest-fix   true}))

  (def vulnerabilities (scan* {:deps-edn-path "resources/vulnerable-deps.edn"
                               :database-strategy "github-advisory"
                               :suggest-fix   true}))

  (controller.output/generate vulnerabilities "deps.edn" "sarif")
  (controller.output/generate vulnerabilities "deps.edn" "stdout-simple"))
