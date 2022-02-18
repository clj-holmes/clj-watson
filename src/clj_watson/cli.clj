(ns clj-watson.cli
  (:gen-class)
  (:require
   [cli-matic.core :as cli]
   [clj-watson.entrypoint :as entrypoint]))

(def CONFIGURATION
  {:app      {:command     "clj-watson"
              :description "run clj-holmes" :version     (System/getProperty "clj-watson.version")}
   :commands [{:command     "scan"
               :description "Performs a scan on a deps.edn file"
               :opts        [{:option  "deps-edn-path" :short "p"
                              :type    :string
                              :default :present
                              :as      "path of deps.edn to scan."}
                             {:option  "output" :short "o"
                              :type    #{"json" "edn" "stdout" "stdout-simple"} ; keep stdout type to avoid break current automations
                              :default "report"
                              :as      "Output type."}
                             {:option "aliases" :short "a"
                              :type :string
                              :multiple true
                              :as "Specify a alias that will have the dependencies analysed alongside with the project deps.It's possible to provide multiple aliases. If a * is provided all the aliases are going to be analysed."}
                             {:option  "dependency-check-properties" :short "d"
                              :type    :string
                              :default nil
                              :as      "[ONLY APPLIED IF USING DEPENDENCY-CHECK STRATEGY] Path of a dependency-check properties file. If not provided uses resources/dependency-check.properties."}
                             {:option "database-strategy" :short "t"
                              :type    #{"dependency-check" "github-advisory"}
                              :default "dependency-check"
                              :as      "Vulnerability database strategy."}
                             {:option "suggest-fix" :short "s"
                              :type    :with-flag
                              :default false
                              :as "Suggest a new deps.edn file fixing all vulnerabilities found."}
                             {:option  "fail-on-result" :short "f"
                              :type    :with-flag
                              :default false
                              :as      "Enable or disable fail if results were found (useful for CI/CD)."}]
               :runs        entrypoint/scan}]})

(defn -main [& args]
  (cli/run-cmd args CONFIGURATION))