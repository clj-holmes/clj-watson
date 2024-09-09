(ns clj-watson.logic.summarize
  (:require
   [clj-watson.logic.utils :as u]
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

(defn final-summary
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

(defn- derive-score
  "Derive a score from severity.
  We want to err on the side of caution, so choose upper bounds.
  We can't say if a High is CVSS2 or CVSS3/4 so convert it to CVSS2 10.0."
  [severity]
  (get {"Critical" 10.0
        "High" 10.0
        "Medium" 6.9
        "Low" 3.9} severity))

(defn- suspicious-score
  "When score looks iffy, return `score` else `nil`"
  [score]
  (when (and (some? score)
             (or (not (number? score))
                 (<= score 0) ;; we consider a score of 0 suspicious
                 (> score 10)))
    score))

(defn- analyze-cvss-scores [{:keys [advisory]}]
  (let [score (-> advisory :cvss :score)
        severity (:severity advisory)
        normalized-severity (when severity (str/capitalize severity))
        suspicious-score (suspicious-score score)
        suspicious-severity (when-not ((set known-severities) normalized-severity)
                              severity)
        severity (if (some? suspicious-severity)
                   severity
                   normalized-severity)
        summary (u/assoc-some {:identifiers (->> advisory :identifiers (mapv :value))}
                              :severity severity
                              :score score
                              :score-version (-> advisory :cvss :version)
                              :suspicious-score suspicious-score)]
    (if-let [derived (cond
                       (nil? score) [:missing-score]
                       (some? suspicious-score) [:suspicious-score])]
      (cond
        (nil? severity)
        (assoc summary
               :score 10.0
               :score-derivation (conj derived :missing-severity))

        (some? suspicious-severity)
        (assoc summary
               :score 10.0
               :suspicious-severity suspicious-severity
               :score-derivation (conj derived :suspicious-severity))

        :else
        (assoc summary
               :score (derive-score severity)
               :score-derivation (conj derived :valid-severity)))
      summary)))

(defn- distill-cvss-scores [vulnerabilities]
  (->> vulnerabilities
       (mapv analyze-cvss-scores)
       (sort-by (juxt :score :identifiers)) ;; include identifiers for consistent sort order
       last))

(defn cvss-threshold-summary
  "Summarize `findings` against `threshold`.

  Returns map with given `:threshold` and `:scores-met` as a vector of maps of:
  - `:identifiers` the vulnerability ids
  - `:dependency` vulnerable dep, e.g. group/artifact
  - `:version` dependency version
  - `:score` cvss or derived score
  - `:severity` cvss severity
  - `:score-version` cvss version (if available)
  - `:suspicious-score` if original score looks suspicious, populated with original score
  - `:suspicious-severity` if severity unrecognized, populated with severity
  - `:score-derivation` a vector describing why score was derived, ex. `[:suspicious-score :missing-severity]`
     - scores are derived when `:missing-score` or `:suspicious-score`
     - and will be derived from a `:valid-severity` else default to critical when `:missing-severity` or `:suspicious-severity`."
  [threshold {:keys [findings]}]
  (let [summary (->> findings
                     (mapv (fn [{:keys [dependency mvn/version vulnerabilities]}]
                             (merge {:dependency dependency
                                     :version version}
                                    (distill-cvss-scores vulnerabilities))))
                     (filterv (fn [{:keys [score]}] (>= score threshold)))
                     (sort-by (juxt :score :dependency)) ;; include dependency for consistent sort order
                     (into []))]
    {:threshold threshold :scores-met summary}))

(comment
  (cvss-threshold-summary 1.0 {:findings [{:dependency "a/b"
                                           :mvn/version "2.1.0"
                                           :vulnerabilities [{:advisory {:identifiers [{:value "cve2"}]
                                                                         :cvss {:score 3.1
                                                                                :version 2.0}
                                                                         :severity "Medium"}}
                                                             {:advisory {:identifiers [{:value "cve3"}]}}]}]})
  ;; => {:threshold 1.0,
  ;;     :scores-met
  ;;     [{:dependency "a/b",
  ;;       :version "2.1.0",
  ;;       :identifiers ["cve3"],
  ;;       :score 10.0,
  ;;       :score-derivation [:missing-score :missing-severity]}]}

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
