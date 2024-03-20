(ns clj-watson.logic.rules.allowlist
  (:require
   [clj-time.core :as time]))

(defn not-expired-bypass?
  ([allowed-cves as-of]
   (partial not-expired-bypass? allowed-cves as-of))
  ([allowed-cves as-of {identifier :value}]
   (when-let [expire-date (get allowed-cves identifier)]
     (time/after? expire-date as-of))))

(defn by-pass?
  [allowed-cves
   as-of
   vulnerability]
  (let [identifiers (-> vulnerability :advisory :identifiers)
        by-passable-cves (filter (not-expired-bypass? allowed-cves as-of) identifiers)]
    (boolean (seq by-passable-cves))))
