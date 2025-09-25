(ns clj-watson.cli
  (:gen-class)
  (:require
   [clj-watson.entrypoint :as entrypoint]))

(defn -main
  "Entrypoint for -M cli usage"
  [& args]
  (let [{:keys [exit]} (entrypoint/scan-main args)]
    (System/exit exit)))
