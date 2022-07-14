(ns clj-watson.adapter.config
  (:require [clj-time.format :as time.format]))

(def time-parser (time.format/formatters :date))

(defn ->allow-config
  [{:keys [cve-label expires]}]
  {cve-label (time.format/parse time-parser expires)})

(defn config->allow-config-map
  [config]
  (->> config
       (map ->allow-config)
       (into {})))

(comment
  (config->allow-config-map [{:cve-label "12" :expires "2021-05-12"}
                             {:cve-label "122" :expires "2026-01-22"}])
  (time.format/parse (time.format/formatters :date) "2021-05-12")
  (clj-time.coerce/from-string "2021-05-12"))