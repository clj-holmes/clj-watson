(ns clj-watson.entrypoint
  (:require [clj-watson.controller.deps :as controller.deps]
            [clj-watson.controller.vulnerability :as controller.vulnerability]))

(defn scan [deps-path aliases]
  (let [{:keys [deps dependencies]} (controller.deps/parse deps-path aliases)
        vulnerable-dependencies (controller.vulnerability/scan-dependencies dependencies)]
    vulnerable-dependencies))

(comment
  (def vulnerable (scan "resources/vulnerable-deps.edn" nil))
  (map :dependency vulnerable))