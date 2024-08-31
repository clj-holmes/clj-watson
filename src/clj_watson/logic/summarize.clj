(ns clj-watson.logic.summarize
  (:require
   [clojure.string :as str]))

(def ^:private known-severities ["Critical" "High" "Medium" "Low"])

(def ^:private known-severities-sort-order
  (reduce (fn [acc [ndx k]]
            (assoc acc k ndx))
          {}
          (map-indexed vector known-severities)))

(defn- sort-by-severity
  "Sort by:
  - recognized severity sort order
  - unrecognized severity by cvss score"
  [vulnerabilities]
  (sort-by (fn [{:keys [advisory]}]
             (let [severity (:severity advisory)
                   severity-order (get known-severities-sort-order severity)
                   score (some-> advisory :cvss :score -)
                   unknown-severity (when-not severity-order
                                      severity)]
               [(or severity-order 999)
                (or score 999)
                unknown-severity]))
           vulnerabilities))

(defn- highest-severity [finding]
  (->> finding
       :vulnerabilities
       sort-by-severity
       first
       :advisory
       :severity))

(defn summarize
  "Account for fact that data is coming from external sources that do not guarantee sensible values.
  Separate expected severities from potentially unrecognized and potentially unspecified.

  A dep can have multiple advisories, we choose the one with the highest severity.

  This summary makes no attempt to distinguish CVSS2 vs CVSS3 vs CVSS4.
  CVSS3 and CVSS4 are similar but CVSS2, for example, has no Critical severity."
  [{:keys [deps-scanned findings]}]
  ;; Dependencies scanned: 151
  ;; Vulnerable dependencies found: 9 (4 Critical, 1 High, 2 Medium), Unrecognized severities: (2 Foobar), Unspecified severities: 3
  (let [findings (mapv #(assoc % :highest-severity (highest-severity %)) findings)
        severity-counts (->> findings
                             (mapv #(some-> % :highest-severity str/capitalize))
                             (frequencies))
        {:keys [expected unexpected unspecified]} (reduce-kv (fn [m k v]
                                                               (cond
                                                                 (nil? k) (assoc m :unspecified v)
                                                                 (get known-severities-sort-order k) (assoc-in m [:expected k] v)
                                                                 :else (assoc-in m [:unexpected k] v)))
                                                             {:unspecified 0}
                                                             severity-counts)
        expected (->> expected
                      (into [])
                      (sort-by #(->> % first (get known-severities-sort-order))))
        unexpected (->> unexpected
                        (into [])
                        (sort-by first))]
    {:cnt-deps-scanned deps-scanned
     :cnt-deps-vulnerable (count findings)
     :cnt-deps-severities expected
     :cnt-deps-unexpected-severities unexpected
     :cnt-deps-unspecified-severity unspecified}))

(comment
  (sort-by-severity [{:advisory {:severity "Critical"}}
                     {:advisory {:severity "Foo"}}
                     {:advisory {:severity "Medium"}}
                     {:advisory {:severity "Aba"}}
                     {:advisory {:severity "Blah" :cvss {:score 8.0}}}
                     {:advisory {:severity "Bah" :cvss {:score 9.0}}}
                     {:advisory {:severity "Low"}}])
  ;; => ({:advisory {:severity "Critical"}}
  ;;     {:advisory {:severity "Medium"}}
  ;;     {:advisory {:severity "Low"}}
  ;;     {:advisory {:severity "Bah", :cvss {:score 9.0}}}
  ;;     {:advisory {:severity "Blah", :cvss {:score 8.0}}}
  ;;     {:advisory {:severity "Aba"}}
  ;;     {:advisory {:severity "Foo"}})

  (sort-by-severity [{:advisory {:severity "Foo"}}
                     {:advisory {:severity "Aba"}}
                     {:advisory {:severity "Bar"   :cvss {:score 9.9}}}
                     {:advisory {:severity "Alpha" :cvss {:score 9.8}}}])
  ;; => ({:advisory {:severity "Bar", :cvss {:score 9.9}}}
  ;;     {:advisory {:severity "Alpha", :cvss {:score 9.8}}}
  ;;     {:advisory {:severity "Aba"}}
  ;;     {:advisory {:severity "Foo"}})

  (sort-by-severity [{:advisory {:severity "Foo"}}
                     {:advisory {:severity "Bar"}}])
  ;; => ({:advisory {:severity "Bar"}} {:advisory {:severity "Foo"}})

  :eoc)
