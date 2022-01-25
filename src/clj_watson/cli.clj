(ns clj-watson.cli
  (:gen-class)
  (:require
   [cli-matic.core :as cli]
   [clj-watson.entrypoint :as entrypoint]))

(def CONFIGURATION
  {:app      {:command     "clj-watson"
              :description "run clj-holmes"
              :version     (System/getProperty "clj-watson.version")}
   :commands [{:command     "scan"
               :description "Performs a scan on a deps.edn file"
               :opts        [{:option  "deps-edn-path" :short "p"
                              :type    :string
                              :default :present
                              :as      "path of deps.edn to scan"}
                             {:option  "dependency-check-properties" :short "d"
                              :type    :string
                              :default nil
                              :as      "path of deps.edn to scan"}
                             {:option  "output" :short "o"
                              :type    #{"stdout" "json" "edn"}
                              :default "stdout"
                              :as      "Output type"}
                             {:option  "fail-on-result" :short "f"
                              :type    :with-flag
                              :default false
                              :as      "Enable or disable fail if results were found (useful for CI/CD)"}]
               :runs        entrypoint/-main}]})

(defn -main [& args]
  (cli/run-cmd args CONFIGURATION))