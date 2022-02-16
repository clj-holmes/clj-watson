(ns clj-watson.entrypoint
  (:require [clj-watson.controller.deps :as controller.deps]
            [clj-watson.controller.remediate :as controller.remediate]
            [clj-watson.controller.output :as controller.output]
            [clj-watson.controller.vulnerability :as controller.vulnerability]))

(defn scan* [{:keys [deps-edn-path suggest-fix aliases]}]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-edn-path aliases)
        vulnerable-dependencies (controller.vulnerability/scan-dependencies dependencies deps)]
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
  (def vulnerabilities (scan* {:deps-edn-path               "resources/vulnerable-deps.edn"
                               :suggest-fix                 true
                               :dependency-check-properties "resources/dependency-check.properties"}))

  (scan* {:deps-edn-path               "/Users/dpr/dev/180seg/golfinho/deps.edn"
          :suggest-fix                 true
          :dependency-check-properties "resources/dependency-check.properties"})
  (controller.output/generate vulnerabilities "stdout"))