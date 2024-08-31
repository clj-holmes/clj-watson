(ns clj-watson.unit.logic.summarize-test
  (:require
   [clj-watson.logic.summarize :as summarize]
   [clojure.test :refer [deftest is]]))

(deftest all-good-test
  (is (= {:cnt-deps-scanned 42
          :cnt-deps-vulnerable 0
          :cnt-deps-severities []
          :cnt-deps-unexpected-severities []
          :cnt-deps-unspecified-severity 0}
         (summarize/summarize {:deps-scanned 42 :findings []}))))

(deftest expected-severities-test
  (is (= {:cnt-deps-scanned 97
          :cnt-deps-vulnerable 7
          :cnt-deps-severities [["Critical" 1] ["High" 3] ["Medium" 2] ["Low" 1]]
          :cnt-deps-unexpected-severities []
          :cnt-deps-unspecified-severity 0}
         (summarize/summarize
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

(deftest unexpected-and-missing-severities-test
  (is (= {:cnt-deps-scanned 7123
          :cnt-deps-vulnerable 10
          :cnt-deps-severities [["Critical" 1] ["High" 2] ["Medium" 1]]
          :cnt-deps-unexpected-severities [["Bar" 2] ["Foo" 1]]
          :cnt-deps-unspecified-severity 3}
         (summarize/summarize
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
