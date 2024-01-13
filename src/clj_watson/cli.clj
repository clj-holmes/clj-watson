(ns clj-watson.cli
  (:gen-class)
  (:require
   [cli-matic.core :as cli]
   [clj-watson.cli-spec :refer [CONFIGURATION]]
   [clj-watson.entrypoint :as entrypoint]))

(defn -main [& args]
  (cli/run-cmd args
               (update-in CONFIGURATION
                          [:commands 0]
                          (assoc :runs entrypoint/scan))))
