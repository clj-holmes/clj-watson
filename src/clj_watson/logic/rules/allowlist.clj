(ns clj-watson.logic.rules.allowlist
  (:require
    [clj-time.core :as time]))

(defn match-cve?
  ([allowed-cves as-of]
   (partial match-cve? allowed-cves as-of))
  ([allowed-cves
    as-of
    {identifier :value}]
   (when-let [expire-date (allowed-cves identifier)]
     (time/after? expire-date as-of))))

(defn by-pass?
  [allowed-cves
   as-of
   vulnerability]
  (let [allowed? (comp seq (partial filter (match-cve? allowed-cves as-of)) :identifiers :advisory)]
    (->> vulnerability
         :vulnerabilities
         (remove allowed?)
         empty?)))
