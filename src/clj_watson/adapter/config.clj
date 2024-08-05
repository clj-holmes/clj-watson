(ns clj-watson.adapter.config
  (:import
   (java.time LocalDate ZoneOffset)))

(defn ->allow-config
  [{:keys [cve-label expires]}]
  {cve-label
   ;; yyyy-MM-dd
   (.atStartOfDay (LocalDate/parse expires) ZoneOffset/UTC)})

(defn config->allow-config-map
  [config]
  (->> config
       :allow-list
       :cves
       (map ->allow-config)
       (into {})))
