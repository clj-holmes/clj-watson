(ns clj-watson.logic.dependency-check.version
  (:require
   [clj-watson.logic.dependency :as logic.dependency])
  (:import
   (org.owasp.dependencycheck.utils DependencyVersion)))

(def ^:private version-operators
  {:version-end-excluding   >
   :version-end-including   >=
   :version-start-excluding <
   :version-start-including <=})

(defn ^:private compare-cpe-version-with-version [version current-version]
  (let [version (DependencyVersion. version)
        current-version (DependencyVersion. current-version)]
    (cond
      (= version current-version) true
      (= version "-") false
      :default false)))

(defn ^:private compare-version-with-current-version [version current-version operator]
  (let [version (DependencyVersion. version)
        current-version (DependencyVersion. current-version)]
    (operator (.compareTo version current-version) 0)))

(defn ^:private check-if-matches-versions [contains-versions? current-version result version-kind version-value]
  (if (and contains-versions? version-value)
    (let [operator (version-kind version-operators)
          compare-result (compare-version-with-current-version version-value current-version operator)]
      (if (or (= version-kind :version-start-including)
              (= version-kind :version-start-excluding))
        (and compare-result result)
        compare-result))
    result))

(defn ^:private check-if-matches-cpe-version [contains-versions? current-version cpe-version]
  (cond
    (= cpe-version "-") false
    (and (not contains-versions?)
         (compare-cpe-version-with-version cpe-version current-version)) false))

(defn vulnerable? [cpe-version versions current-version]
  (let [contains-versions? (->> versions vals (some string?) boolean)]
    (or (check-if-matches-cpe-version contains-versions? current-version cpe-version)
        (reduce-kv (partial check-if-matches-versions contains-versions? current-version) false versions))))

(defn newer-and-not-vulnerable-version? [cpe-version versions current-version version-to-check]
  (let [version-to-check (logic.dependency/get-dependency-version version-to-check)]
    (if (>= (.compareTo (DependencyVersion. version-to-check) (DependencyVersion. current-version)) 1)
      (not (vulnerable? cpe-version versions version-to-check)))))

(comment
  (vulnerable? "1.2.0" "*" {:version-start-excluding nil
                            :version-start-including "1.1.0"
                            :version-end-including nil
                            :version-end-excluding nil}))