(ns clj-watson.unit.adapter.config-test
  (:require
   [clj-watson.adapter.config :as adapter.config]
   [clj-watson.test-util :as tu]
   [clojure.test :refer :all])
  (:import
   (java.time ZonedDateTime)
   (java.time.format DateTimeParseException)))

(use-fixtures :each tu/pool-debug-fixture)

(deftest ->allow-config
  (testing "Allow Parsing"
    (is (= {"CVE-1234" (ZonedDateTime/parse "2021-05-12T00:00:00Z")}
           (adapter.config/->allow-config {:cve-label "CVE-1234" :expires "2021-05-12"})))
    (is (thrown? DateTimeParseException
                 (adapter.config/->allow-config {:cve-label "CVE-1234" :expires "wrong date"})))))

(deftest config->allow-list
  (testing "Configs Parsing"
    (is (= {"CVE-1234" (ZonedDateTime/parse "2021-05-12T00:00:00Z")
            "CVE-5678" (ZonedDateTime/parse "2025-05-12T00:00:00Z")}
           (adapter.config/config->allow-config-map {:allow-list {:cves [{:cve-label "CVE-1234" :expires "2021-05-12"}
                                                                         {:cve-label "CVE-5678" :expires "2025-05-12"}]}})))))
