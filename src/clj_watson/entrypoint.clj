(ns clj-watson.entrypoint
  (:require
   [clj-watson.controller.dependency-check :as controller.dependency-check]
   [clj-watson.controller.output :as controller.output]
   [clj-watson.controller.vulnerability :as controller.vulnerability]
   [clj-watson.diplomat.remediate :as diplomat.remediate]))

(defn scan [{:keys [deps-edn-path dependency-check-properties suggest-fix]}]
  (let [environment (controller.dependency-check/scan-dependencies deps-edn-path dependency-check-properties)
        vulnerabilities (controller.vulnerability/extract-from-dependencies environment)]
    (if suggest-fix
      (diplomat.remediate/vulnerabilities-fix-suggestions vulnerabilities deps-edn-path)
      vulnerabilities)))

(defn -main [{:keys [fail-on-result output] :as opts}]
  (let [vulnerabilities (scan opts)]
    (controller.output/generate vulnerabilities output)
    (if (and (-> vulnerabilities count (> 0))
             fail-on-result)
      (System/exit 1)
      (System/exit 0))))

(comment
  (scan {:deps-edn-path               "resources/vulnerable-deps.edn"
         :dependency-check-properties "resources/dependency-check.properties"}))