(ns clj-watson.unit.logic.rules.allowlist-test
  (:require
   [clj-time.core :as time]
   [clj-watson.logic.rules.allowlist :as logic.rules.allowlist]
   [clojure.test :refer :all]))

(def before-of (time/date-time 2023 3 3))
(def as-of (time/date-time 2024 4 4))
(def after-of (time/date-time 2025 5 5))

(deftest empty-allowlist-match-cve?
  (is (nil?
       (logic.rules.allowlist/match-cve?
        {}
        as-of
        {:value "GHSA-4265-ccf5-phj5"}))))

(deftest match-cve?
  (is (true?
       (logic.rules.allowlist/match-cve?
        {"GHSA-4265-ccf5-phj5" after-of}
        as-of
        {:value "GHSA-4265-ccf5-phj5"})))
  (is (false?
       (logic.rules.allowlist/match-cve?
        {"GHSA-4265-ccf5-phj5" before-of}
        as-of
        {:value "GHSA-4265-ccf5-phj5"}))))
