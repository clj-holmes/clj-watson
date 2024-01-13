(ns clj-watson.adapter.config
  (:require
   [clj-time.format :as time.format]))

(def time-parser (time.format/formatters :date))

(defn ->allow-config
  [{:keys [cve-label expires]}]
  {cve-label (time.format/parse time-parser expires)})

(defn config->allow-config-map
  [config]
  (->> config
       :allow-list
       :cves
       (map ->allow-config)
       (into {})))

(comment
  (config->allow-config-map nil)
  )
