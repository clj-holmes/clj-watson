(ns clj-watson.entrypoint
  (:require [clj-watson.controller.deps :as controller.deps]
            [clj-watson.controller.remediate :as controller.remediate]
            [clj-watson.controller.vulnerability :as controller.vulnerability]))

(defn scan [deps-path aliases]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-path aliases)
        vulnerable-dependencies (controller.vulnerability/scan-dependencies dependencies deps)
        vulnerable-dependencies-with-remediate-suggestion (controller.remediate/scan vulnerable-dependencies deps)]
    vulnerable-dependencies-with-remediate-suggestion))

(comment
  (def vulnerable (scan "resources/vulnerable-deps.edn" nil))
  (def vulnerable (scan "deps.edn" nil)))