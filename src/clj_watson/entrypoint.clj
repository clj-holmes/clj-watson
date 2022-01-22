(ns clj-watson.entrypoint
  (:gen-class)
  (:require [clj-watson.controller.dependency-check :as controller.dependency-check]
            [clj-watson.controller.output.report :as controller.report]
            [clj-watson.controller.vulnerability :as controller.vulnerability]))

(defn -main [deps-edn-path dependency-check-properties]
  (let [environment (controller.dependency-check/scan-dependencies deps-edn-path dependency-check-properties)
        vulnerabilities (controller.vulnerability/extract-from-dependencies environment)]
    (controller.report/generate vulnerabilities))
  (shutdown-agents))

(comment
  (def result (-main "deps.edn" "resources/dependency-check.properties"))
  (def result (-main "resources/vulnerable-deps.edn" "resources/dependency-check.properties")0))