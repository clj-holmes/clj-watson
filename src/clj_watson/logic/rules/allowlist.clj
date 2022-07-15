(ns clj-watson.logic.rules.allowlist
  (:require
   [clj-time.core :as time]))

(defn by-pass?
  [config-map as-of vulnerability]
  (let [match-cve? (fn [idetifier] (= (:value idetifier) (:cve-label config-map)))
        allowed? (comp seq (partial filter match-cve?) :identifiers :advisory first :vulnerabilities)] ;TODO: double check first usage
    (boolean (and (allowed? vulnerability)
                  (time/after? (:expires config-map) as-of)))))
