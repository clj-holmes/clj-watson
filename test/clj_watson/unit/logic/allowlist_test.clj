(ns clj-watson.unit.logic.allowlist-test
  (:require
   [clj-time.core :as time]
   [clj-watson.logic.rules.allowlist :as logic.rules.allowlist]
   [clojure.test :refer :all]))

(deftest by-pass?
  (let [expired-date (time/local-date 2020 2 1)
        as-of (time/local-date 2022 7 12)
        valid-date (time/local-date 2022 7 14)]
    (testing "matching CVEs"
      (is (= true (logic.rules.allowlist/by-pass? {:cve-label "CVE-2022-2047"
                                                   :expires   valid-date}
                                                  as-of
                                                  {:vulnerabilities
                                                   [{:advisory
                                                     {:identifiers
                                                      [{:value "GHSA-cj7v-27pg-wf7q"}
                                                       {:value "CVE-2022-2047"}]}}]})))
      (is (= false (logic.rules.allowlist/by-pass? {:cve-label "CVE-2022-2042"
                                                    :expires   valid-date}
                                                   as-of
                                                   {:vulnerabilities
                                                    [{:advisory
                                                      {:identifiers
                                                       [{:value "GHSA-cj7v-27pg-wf7q"}
                                                        {:value "CVE-DO-NOT-BYPASS"}]}}]}))))
    (testing "expired allowlist"
      (is (= false (logic.rules.allowlist/by-pass? {:cve-label "CVE-2022-2047"
                                                    :expires   expired-date}
                                                   as-of
                                                   {:vulnerabilities
                                                    [{:advisory
                                                      {:identifiers
                                                       [{:value "GHSA-cj7v-27pg-wf7q"}
                                                        {:value "CVE-2022-2047"}]}}]})))
      (is (= false (logic.rules.allowlist/by-pass? {:cve-label "CVE-2022-2042"
                                                    :expires   expired-date}
                                                   as-of
                                                   {:vulnerabilities
                                                    [{:advisory
                                                      {:identifiers
                                                       [{:value "GHSA-cj7v-27pg-wf7q"}
                                                        {:value "CVE-DO-NOT-BYPASS"}]}}]}))))))