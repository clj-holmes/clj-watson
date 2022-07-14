(ns clj-watson.unit.adapter.config-test
  (:require [clojure.test :refer :all]
            [clj-watson.adapter.config :as adapter.config]
            [clj-time.core :as time]
            [matcher-combinators.test]))

(deftest ->allow-config
  (testing "Config Parsing"
    (is (match? {"CVE-1234" (time/date-time 2021 5 12)}
                (adapter.config/->allow-config {:cve-label "CVE-1234" :expires "2021-05-12"})))
    (is (thrown-match? IllegalArgumentException
                (adapter.config/->allow-config {:cve-label "CVE-1234" :expires "wrong date"})))
    ))

(->allow-config)