(ns clj-watson.unit.adapter.config-test
  (:require
   [clj-time.core :as time]
   [clj-watson.adapter.config :as adapter.config]
   [clojure.test :refer :all]))

(deftest ->allow-config
  (testing "Allow Parsing"
    (is (= {"CVE-1234" (time/date-time 2021 5 12)}
           (adapter.config/->allow-config {:cve-label "CVE-1234" :expires "2021-05-12"})))
    (is (thrown? IllegalArgumentException
                 (adapter.config/->allow-config {:cve-label "CVE-1234" :expires "wrong date"})))))

(deftest config->allow-list
  (testing "Configs Parsing"
    (is (= {"CVE-1234" (time/date-time 2021 5 12)
            "CVE-5678" (time/date-time 2025 5 12)}
           (adapter.config/config->allow-config-map {:allow-list {:cves [{:cve-label "CVE-1234" :expires "2021-05-12"}
                                                                         {:cve-label "CVE-5678" :expires "2025-05-12"}]}})))))
