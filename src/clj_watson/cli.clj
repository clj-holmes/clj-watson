(ns clj-watson.cli
  (:gen-class)
  (:require
   [clj-watson.cli-spec :as cli-spec]
   [clj-watson.entrypoint :as entrypoint]))

(defn -main
  "Entrypoint for -M cli usage"
  [& args]
  (entrypoint/do-scan (cli-spec/parse-args args)))
