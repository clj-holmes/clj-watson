(ns clj-watson.unit.logic.summarize-test
  (:require
   [clj-watson.logic.summarize :as summarize]
   [clj-watson.test-util :as tu]
   [clojure.test :refer [deftest is use-fixtures]]))

(use-fixtures :each tu/pool-debug-fixture)

(deftest final-summary-all-good-test
  (is (= {:cnt-deps-scanned 42
          :cnt-deps-vulnerable 0
          :cnt-deps-severities []
          :cnt-deps-unexpected-severities []
          :cnt-deps-unspecified-severity 0}
         (summarize/final-summary {:deps-scanned 42 :findings []}))))

(deftest final-summary-expected-severities-test
  (is (= {:cnt-deps-scanned 97
          :cnt-deps-vulnerable 7
          :cnt-deps-severities [["Critical" 1] ["High" 3] ["Medium" 2] ["Low" 1]]
          :cnt-deps-unexpected-severities []
          :cnt-deps-unspecified-severity 0}
         (summarize/final-summary
          {:deps-scanned 97
           :findings [{:vulnerabilities [{:advisory {:severity "High"}}]}   ;; high wins
                      {:vulnerabilities [{:advisory {:severity "Wtf"}}
                                         {:advisory {:severity "Low"}}
                                         {:advisory {:severity "High"}}]}   ;; high wins
                      {:vulnerabilities [{:advisory {:severity "High"}}     ;; high wins
                                         {:advisory {:severity "Medium"}}
                                         {:advisory {:severity "Wtf"}}]}
                      {:vulnerabilities [{:advisory {:severity "Foo"}}
                                         {:advisory {:severity "Low"}}]}    ;; low wins
                      {:vulnerabilities [{:advisory {:severity "High"}}
                                         {:advisory {:severity "Low"}}
                                         {:advisory {:severity "Medium"}}
                                         {:advisory {:severity "Critical"}}
                                         {:advisory {:severity "Wtf"}}]}    ;; critical wins
                      {:vulnerabilities [{:advisory {:severity "Medium"}}]} ;; medium wins
                      {:vulnerabilities [{:advisory {:severity "Low"}}
                                         {:advisory {:severity "Medium"}}]} ;; medium wins                  ]}
                      ]}))))

(deftest final-summary-unexpected-and-missing-severities-test
  (is (= {:cnt-deps-scanned 7123
          :cnt-deps-vulnerable 10
          :cnt-deps-severities [["Critical" 1] ["High" 2] ["Medium" 1]]
          :cnt-deps-unexpected-severities [["Bar" 2] ["Foo" 1]]
          :cnt-deps-unspecified-severity 3}
         (summarize/final-summary
          {:deps-scanned 7123
           :findings [{:vulnerabilities [{:advisory {:severity "High"}}]}   ;; high wins
                      {:vulnerabilities [{:advisory {:severity "Wtf"}}
                                         {:advisory {:severity "Low"}}
                                         {:advisory {:severity "High"}}
                                         {:advisory {:severity "High"}}]}   ;; high wins
                      {:vulnerabilities [{:advisory {:severity "Foo"}}
                                         {:advisory {:severity "Bar"}}]}    ;; bar wins
                      {:vulnerabilities [{:advisory {:severity "Foo"}}]}    ;; foo wins
                      {}                                                    ;; unspecified
                      {:vulnerabilities []}                                 ;; unspecified
                      {:vulnerabilities [{:advisory {:severity nil}}]}      ;; unspecified
                      {:vulnerabilities [{:advisory {:severity "High"}}
                                         {:advisory {:severity "Low"}}
                                         {:advisory {:severity "Low"}}
                                         {:advisory {:severity "Medium"}}
                                         {:advisory {:severity "Low"}}
                                         {:advisory {:severity "Medium"}}
                                         {:advisory {:severity "Critical"}} ;; critical wins
                                         {:advisory {:severity "Wtf"}}]}
                      {:vulnerabilities [{:advisory {:severity "Medium"}}]} ;; medium wins
                      {:vulnerabilities [{:advisory {:severity "Foo"}}
                                         {:advisory {:severity "Aba"}}
                                         {:advisory {:severity "Bar"   :cvss {:score 9.9}}} ;; bar wins
                                         {:advisory {:severity "Alpha" :cvss {:score 9.8}}}]}]}))))

(deftest cvss-thresold-summary-test
  (let [filler-advisories [{:advisory {:identifiers [{:value "cvec"}]
                                       :cvss {:score 3.1 :version 2.0}}}
                           {:advisory {:identifiers [{:value "cved"}]
                                       :cvss {:score 3.2 :version 2.0}}}]
        findings [{:dependency "zzz/with-score" :mvn/version "2.1.0"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :cvss {:score 7.7 :version 3.1}}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 7.7 :version 3.0}}}]}
                  {:dependency "yyy/missing-score-missing-severity" :mvn/version "2.1.1"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}]   ;; loses due to sort ordering
                                                 :cvss {:score 10.0 :version 3.0}}}]}
                  {:dependency "xxx/suspicious-score-missing-severity" :mvn/version "2.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :cvss {:score 0.0 :version 2.0}}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 10.0 :version 3.0}}}]}
                  {:dependency "www/suspicious-score-missing-severity2" :mvn/version "2.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :cvss {:score "not-a-score"}}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 10.0 :version 3.0}}}]}
                  {:dependency "vvv/suspicious-score-low-severity" :mvn/version "3.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :severity "LOW"
                                                 :cvss {:score 10.1}}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 3.9 :version 3.0}}}]}
                  {:dependency "uuu/missing-score-medium-severity" :mvn/version "3.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :severity "MEDIUM"}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}]  ;; loses due to sort ordering
                                                 :cvss {:score 6.9 :version 3.0}}}]}
                  {:dependency "ttt/suspicious-score-high-severity" :mvn/version "3.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :severity "HIGH"
                                                 :cvss {:score -12.3}}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 10.0 :version 3.0}}}]}
                  {:dependency "sss/suspicious-score-critical-severity" :mvn/version "3.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :severity "CRITICAL"
                                                 :cvss {:score "nope"}}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 10.0 :version 3.0}}}]}
                  {:dependency "rrr/missing-score-suspicious-severity" :mvn/version "3.1.2"
                   :vulnerabilities [{:advisory {:identifiers [{:value "cveb"}]
                                                 :severity "not-a-severity"}} ;; expected winner
                                     {:advisory {:identifiers [{:value "cvea"}] ;; loses due to sort ordering
                                                 :cvss {:score 10.0 :version 3.0}}}]}]
        findings (->> findings
                      (mapv #(update % :vulnerabilities into filler-advisories))
                      (mapv #(update % :vulnerabilities shuffle))
                      shuffle)
        expected-scores-met [{:dependency "vvv/suspicious-score-low-severity"
                              :version "3.1.2"
                              :identifiers ["cveb"]
                              :score 3.9 ;; upper bound low for CVSS2/3/4
                              :severity "Low"
                              :suspicious-score 10.1
                              :score-derivation [:suspicious-score :valid-severity]}
                             {:dependency "uuu/missing-score-medium-severity"
                              :version "3.1.2"
                              :identifiers ["cveb"]
                              :score 6.9 ;; upper bound medium for CVSS2/3/4
                              :severity "Medium"
                              :score-derivation [:missing-score :valid-severity]}
                             {:dependency "zzz/with-score"
                              :version "2.1.0"
                              :identifiers ["cveb"]
                              :score 7.7
                              :score-version 3.1}
                             {:dependency "rrr/missing-score-suspicious-severity"
                              :version "3.1.2"
                              :identifiers ["cveb"]
                              :score 10.0
                              :severity "not-a-severity"
                              :suspicious-severity "not-a-severity"
                              :score-derivation [:missing-score :suspicious-severity]}
                             {:dependency "sss/suspicious-score-critical-severity"
                              :version "3.1.2"
                              :identifiers ["cveb"]
                              :score 10.0 ;; upper bound critical (for CVSS3)
                              :severity "Critical"
                              :suspicious-score "nope"
                              :score-derivation [:suspicious-score :valid-severity]}
                             {:dependency "ttt/suspicious-score-high-severity"
                              :version "3.1.2"
                              :identifiers ["cveb"]
                              :score 10.0 ;; upper bound high (for CVSS2)
                              :severity "High"
                              :suspicious-score -12.3
                              :score-derivation [:suspicious-score :valid-severity]}
                             {:dependency "www/suspicious-score-missing-severity2"
                              :version "2.1.2"
                              :identifiers ["cveb"]
                              :score 10.0
                              :suspicious-score "not-a-score"
                              :score-derivation [:suspicious-score :missing-severity]}
                             {:dependency "xxx/suspicious-score-missing-severity"
                              :version "2.1.2"
                              :identifiers ["cveb"]
                              :score 10.0
                              :score-version 2.0
                              :suspicious-score 0.0
                              :score-derivation [:suspicious-score :missing-severity]}
                             {:dependency "yyy/missing-score-missing-severity"
                              :version "2.1.1"
                              :identifiers ["cveb"]
                              :score 10.0
                              :score-derivation [:missing-score :missing-severity]}]]
    (is (= {:threshold 0.0
            :scores-met expected-scores-met}
           (summarize/cvss-threshold-summary 0.0 {:findings findings}))
        "all scores met")
    (is (= {:threshold 10
            :scores-met (filterv #(>= (:score %) 10) expected-scores-met)}
           (summarize/cvss-threshold-summary 10 {:findings findings}))
        "some scores met")
    (is (= {:threshold 10.1
            :scores-met []}
           (summarize/cvss-threshold-summary 10.1 {:findings findings}))
        "no scores met")))
