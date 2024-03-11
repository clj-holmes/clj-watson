(ns clj-watson.unit.logic.rules.allowlist-test
  (:require
   [clj-time.core :as time]
   [clj-watson.logic.rules.allowlist :as logic.rules.allowlist]
   [clojure.test :refer :all]))

(def expired-as-of (time/date-time 2023 3 3))
(def as-of (time/date-time 2024 4 4))
(def valid-as-of (time/date-time 2025 5 5))

(deftest empty-bypass?
  (is (nil?
       (logic.rules.allowlist/not-expired-bypass?
        {}
        as-of
        {:value "GHSA-4265-ccf5-phj5"}))))

(deftest not-expired-bypass?
  (is (true?
       (logic.rules.allowlist/not-expired-bypass?
        {"GHSA-4265-ccf5-phj5" valid-as-of}
        as-of
        {:value "GHSA-4265-ccf5-phj5"})))
  (is (false?
       (logic.rules.allowlist/not-expired-bypass?
        {"GHSA-4265-ccf5-phj5" expired-as-of}
        as-of
        {:value "GHSA-4265-ccf5-phj5"}))))

(deftest by-pass?
  (is (false? (logic.rules.allowlist/by-pass? {} as-of {:vulnerableVersionRange ">= 1.21, < 1.26.0",
                                                        :advisory               {:description "Allocation of Resources Without Limits or Throttling vulnerability in Apache Commons Compress. This issue affects Apache Commons Compress: from 1.21 before 1.26.

                           Users are recommended to upgrade to version 1.26, which fixes the issue.

                           ",
                                                                                 :summary     "Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 file",
                                                                                 :severity    "HIGH",
                                                                                 :cvss        {:score 7.5},
                                                                                 :identifiers [{:value "GHSA-4265-ccf5-phj5"} {:value "CVE-2024-26308"}]},
                                                        :firstPatchedVersion    {:identifier "1.26.0"}})))
  (is (false? (logic.rules.allowlist/by-pass? {"GHSA-4265-ccf5-phj5" expired-as-of} as-of {:vulnerableVersionRange ">= 1.21, < 1.26.0",
                                                                                           :advisory                                                  {:description "Allocation of Resources Without Limits or Throttling vulnerability in Apache Commons Compress. This issue affects Apache Commons Compress: from 1.21 before 1.26.

                           Users are recommended to upgrade to version 1.26, which fixes the issue.

                           ",
                                                                                                                                                       :summary     "Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 file",
                                                                                                                                                       :severity    "HIGH",
                                                                                                                                                       :cvss        {:score 7.5},
                                                                                                                                                       :identifiers [{:value "GHSA-4265-ccf5-phj5"} {:value "CVE-2024-26308"}]},
                                                                                           :firstPatchedVersion                                       {:identifier "1.26.0"}})))
  (is (true? (logic.rules.allowlist/by-pass? {"GHSA-4265-ccf5-phj5" valid-as-of} as-of {:vulnerableVersionRange ">= 1.21, < 1.26.0",
                                                                                        :advisory                {:description "Allocation of Resources Without Limits or Throttling vulnerability in Apache Commons Compress. This issue affects Apache Commons Compress: from 1.21 before 1.26.

                           Users are recommended to upgrade to version 1.26, which fixes the issue.

                           ",
                                                                                                                  :summary     "Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 file",
                                                                                                                  :severity    "HIGH",
                                                                                                                  :cvss        {:score 7.5},
                                                                                                                  :identifiers [{:value "GHSA-4265-ccf5-phj5"}]},
                                                                                        :firstPatchedVersion     {:identifier "1.26.0"}})))
  (is (true? (logic.rules.allowlist/by-pass? {"GHSA-4265-ccf5-phj5" valid-as-of} as-of {:vulnerableVersionRange ">= 1.21, < 1.26.0",
                                                                                        :advisory                {:description "Allocation of Resources Without Limits or Throttling vulnerability in Apache Commons Compress. This issue affects Apache Commons Compress: from 1.21 before 1.26.

                           Users are recommended to upgrade to version 1.26, which fixes the issue.

                           ",
                                                                                                                  :summary     "Apache Commons Compress: OutOfMemoryError unpacking broken Pack200 file",
                                                                                                                  :severity    "HIGH",
                                                                                                                  :cvss        {:score 7.5},
                                                                                                                  :identifiers [{:value "GHSA-4265-ccf5-phj5"} {:value "CVE-2024-26308"}]},
                                                                                        :firstPatchedVersion     {:identifier "1.26.0"}}))))
