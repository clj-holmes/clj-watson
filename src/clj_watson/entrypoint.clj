(ns clj-watson.entrypoint
  (:require [clj-watson.controller.deps :as controller.deps]
            [clj-watson.controller.remediate :as controller.remediate]
            [clj-watson.controller.output :as controller.output]
            [clj-watson.controller.vulnerability :as controller.vulnerability]))

(defn scan* [{:keys [deps-edn-path suggest-fix aliases]}]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        repositories (select-keys deps [:mvn/repos])
        vulnerable-dependencies (controller.vulnerability/scan-dependencies dependencies repositories)]
    (if suggest-fix
      (controller.remediate/scan vulnerable-dependencies deps)
      vulnerable-dependencies)))

(defn scan [{:keys [fail-on-result output] :as opts}]
  (let [vulnerabilities (scan* opts)]
    (controller.output/generate vulnerabilities output)
    (if (and (-> vulnerabilities count (> 0))
             fail-on-result)
      (System/exit 1)
      (System/exit 0))))

(comment
  (def vulnerabilities (scan* {:deps-edn-path "resources/vulnerable-deps.edn"
                               :suggest-fix   true}))

  (controller.output/generate vulnerabilities "report")
  (controller.output/generate vulnerabilities "full-report"))