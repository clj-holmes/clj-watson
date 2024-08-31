(ns clj-watson.unit.controller.output-test
  (:require
   [clj-watson.controller.output :as output]
   [clojure.test :refer [deftest is]]))

(deftest final-summary-all-good-test
  (is (= (str "Dependencies scanned: 72\n"
              "Vulnerable dependencies found: 0\n")
         (with-out-str (output/final-summary {:cnt-deps-scanned 72
                                              :cnt-deps-vulnerable 0})))))

(deftest final-summary-expected-severities-test
  (is (= (str "Dependencies scanned: 72\n"
              "Vulnerable dependencies found: 10 (3 Critical, 2 High, 1 Medium, 4 Low)\n")
         (with-out-str (output/final-summary {:cnt-deps-scanned 72
                                              :cnt-deps-vulnerable 10
                                              :cnt-deps-unspecified-severity 0
                                              :cnt-deps-severities [["Critical" 3]
                                                                    ["High" 2]
                                                                    ["Medium" 1]
                                                                    ["Low" 4]]})))))
(deftest final-summary-unexpected-and-unspecified-severities-test
  (is (= (str "Dependencies scanned: 72\n"
              "Vulnerable dependencies found: 11 (7 Critical), Unrecognized severities: (2 Foo, 7 Bar), Unspecified severities: 7\n")
         (with-out-str (output/final-summary {:cnt-deps-scanned 72
                                              :cnt-deps-vulnerable 11 ;; garbage in, garbage out
                                              :cnt-deps-unspecified-severity 7
                                              :cnt-deps-unexpected-severities [["Foo" 2] ["Bar" 7]]
                                              :cnt-deps-severities [["Critical" 7]]})))))

(deftest cvss-threshold-summary-all-good-test
  (is (= "No scores met CVSS fail threshold of 2.2\n"
         (with-out-str (output/cvss-threshold-summary {:threshold 2.2 :scores-met []})))))

(deftest cvss-thresold-summary-test
  (is (= "CVSS fail score threshold of 1.7 met for:

  Dependency                           Version Identifiers CVSS Score
  all/good                             1.2.3   id1         1.9 (version 4.0)
  missing/version                      1.2.4   id2 id3     2.0 (version <missing>)
  missing-score/severity-valid         3.3.3   id4         3.5 (score missing - derived from Low severity)
  suspicious-score/severity-valid      2.2.2   id5         10.0 (score 0.0 suspicious - derived from High severity)
  missing-score/severity-missing       2.2.3   id6         10.0 (score missing and severity missing)
  suspicious-score/severity-missing    2.2.4   id7         10.0 (score 12.2 suspicious and severity missing)
  missing-score/severity-suspicious    2.2.3   id8         10.0 (score missing and severity moodog unrecognized)
  suspicious-score/severity-suspicious 2.2.4   id9         10.0 (score 12.2 suspicious and severity blarg unrecognized)
"
         (with-out-str (output/cvss-threshold-summary
                        {:threshold 1.7 :scores-met
                         [{:identifiers ["id1"]
                           :dependency "all/good"
                           :version "1.2.3"
                           :score 1.9
                           :score-version "4.0"}
                          {:identifiers ["id2" "id3"]
                           :dependency "missing/version"
                           :version "1.2.4"
                           :score 2.0}
                          {:identifiers ["id4"]
                           :dependency "missing-score/severity-valid"
                           :version "3.3.3"
                           :score 3.5 ;; we just present here, we don't derive
                           :score-derivation [:missing-score :valid-severity]
                           :severity "Low"}
                          {:identifiers ["id5"]
                           :dependency "suspicious-score/severity-valid"
                           :version "2.2.2"
                           :score 10.0
                           :suspicious-score 0.0
                           :score-derivation [:suspicious-score :valid-severity]
                           :severity "High"}
                          {:identifiers ["id6"]
                           :dependency "missing-score/severity-missing"
                           :version "2.2.3"
                           :score 10.0
                           :score-derivation [:missing-score :missing-severity]}
                          {:identifiers ["id7"]
                           :dependency "suspicious-score/severity-missing"
                           :version "2.2.4"
                           :score 10.0
                           :suspicious-score 12.2
                           :score-derivation [:suspicious-score :missing-severity]}
                          {:identifiers ["id8"]
                           :dependency "missing-score/severity-suspicious"
                           :version "2.2.3"
                           :score 10.0
                           :suspicious-severity "moodog"
                           :score-derivation [:missing-score :suspicious-severity]}
                          {:identifiers ["id9"]
                           :dependency "suspicious-score/severity-suspicious"
                           :version "2.2.4"
                           :score 10.0
                           :suspicious-score 12.2
                           :suspicious-severity "blarg"
                           :score-derivation [:suspicious-score :suspicious-severity]}]})))))
