(ns clj-watson.cli
  (:gen-class)
  (:require
   [clj-watson.entrypoint :as entrypoint]))

;; mock/capture target
(defn system-exit [code]
  (System/exit code))

(defn -main
  "Entrypoint for -M cli usage"
  [& args]
  (let [{:keys [exit]} (entrypoint/scan-main args)]
    (shutdown-agents)
    (when exit
      (system-exit exit))))
