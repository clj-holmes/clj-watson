(ns clj-watson.controller.output
  (:require
   [cheshire.core :as json]
   [clj-watson.logic.sarif :as logic.sarif]
   [clj-watson.logic.table :as table]
   [clj-watson.logic.template :as logic.template]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(defmulti ^:private generate* (fn [_ _ kind] kind))

(defmethod ^:private generate* :stdout-simple [dependencies & _]
  (let [template (-> "simple-report.mustache" io/resource slurp)]
    (println (logic.template/generate {:vulnerable-dependencies dependencies} template))))

(defmethod ^:private generate* :stdout [dependencies & _]
  (let [template (-> "full-report.mustache" io/resource slurp)]
    (println (logic.template/generate {:vulnerable-dependencies dependencies} template))))

(defmethod ^:private generate* :json [dependencies & _]
  (-> dependencies (json/generate-string {:pretty true}) println))

(defmethod ^:private generate* :edn [dependencies & _]
  (pprint/pprint dependencies))

(defmethod ^:private generate* :sarif [dependencies deps-edn-path & _]
  (-> dependencies (logic.sarif/generate deps-edn-path) (json/generate-string {:pretty true}) println))

(defn generate [dependencies deps-edn-path kind]
  (generate* dependencies deps-edn-path kind))

(defn final-summary
  "See `clj-waston.logic.summarize/summary` for description"
  [{:keys [cnt-deps-scanned cnt-deps-vulnerable
           cnt-deps-severities cnt-deps-unexpected-severities cnt-deps-unspecified-severity]}]
  (let [details (->> [(when (seq cnt-deps-severities)
                        (format "(%s)"
                                (->> cnt-deps-severities
                                     (mapv (fn [[severity count]] (format "%d %s" count severity)))
                                     (str/join ", "))))
                      (when (seq cnt-deps-unexpected-severities)
                        (format "Unrecognized severities: (%s)"
                                (->> cnt-deps-unexpected-severities
                                     (mapv (fn [[severity count]] (format "%d %s" count severity)))
                                     (str/join ", "))))
                      (when (and cnt-deps-unspecified-severity (> cnt-deps-unspecified-severity 0))
                        (format "Unspecified severities: %d" cnt-deps-unspecified-severity))]
                     (keep identity))]
    ;; Dependencies scanned: 151
    ;; Vulnerable dependencies found: 9 (4 Critical, 1 High, 2 Medium), Unrecognize d severities: (2 Foobar), Unspecified severities: 3
    (println (format "Dependencies scanned: %d" cnt-deps-scanned))
    (println (format "Vulnerable dependencies found: %d%s"
                     cnt-deps-vulnerable
                     (if (seq details)
                       (str " " (str/join ", " details))
                       "")))))

(defn- cvss-threshold-summary->rows [opts data]
  {:rows
   (into [(mapv #(:title %) opts)]
         (mapv (fn [s]
                 (mapv (fn [{:keys [key]}]
                         (-> s key str))
                       opts))
               data))})

(defn cvss-threshold-summary [{:keys [threshold scores-met]}]
  ;; CVSS fail score threshold of 2.0 met for:
  ;;
  ;;  Dependency Version Identifiers CVSS Score
  ;;  foo/bar    1.2.3   CVE-1231    5.2 (version 3.1)
  ;;  foo/bar1   7.7a    CVE-123134  6.9 (score missing - derived from Medium severity)
  ;;  foo/bar2   2.0     CVE-12313   10.0 (score missing - derived from High severity)
  ;;  foo/bar3   24.2    CVE-188     10.0 (score missing - derived from Critical severity)
  ;;  foo/bar4   8.2     CVE-123132  10.0 (score missing and severity Foo unrecognized)
  ;;  foo/bar5   1.12    CVE-12313   10.0 (score and severity missing)
  ;;  foo/bar6   1.12    CVE-1232    10.0 (score 0.0 suspicious and severity missing)
  ;;  foo/bar6   1.12    CVE-1237    10.0 (score foo suspicious and severity missing)
  ;;  foo/bar6   1.12    CVE-1238    10.0 (score 11.2 suspicious and severity missing)
  (if-not (seq scores-met)
    (println (format "No scores met CVSS fail threshold of %s" threshold))
    (do
      (println (format "CVSS fail score threshold of %s met for:\n" threshold))
      (->> scores-met
           (mapv (fn [{:keys [identifiers score severity score-version score-derivation suspicious-score suspicious-severity] :as m}]
                   (assoc m
                          :identifiers (str/join " " identifiers)
                          :score-desc
                          (format "%s (%s)"
                                  score
                                  (if-let [[score-analysis severity-analysis] score-derivation]
                                    (format "%s %s"
                                            (case score-analysis
                                              :missing-score "score missing"
                                              :suspicious-score (format "score %s suspicious" suspicious-score))
                                            (case severity-analysis
                                              :valid-severity (format "- derived from %s severity" severity)
                                              :missing-severity "and severity missing"
                                              :suspicious-severity (format "and severity %s unrecognized" suspicious-severity)))
                                    (format "version %s" (or score-version "<missing>")))))))
           (cvss-threshold-summary->rows [{:key :dependency  :title "Dependency"}
                                          {:key :version     :title "Version"}
                                          {:key :identifiers :title "Identifiers"}
                                          {:key :score-desc  :title "CVSS Score"}])
           table/format-table
           println))))
