(ns clj-watson.unit.entrypoint-test
  "Sanity -X/-T tests. Because scanning takes a very long time, we use mocks.
  See -M variants in clj-watson.unit.cli-test "
  (:require
   [clj-watson.controller.dependency-check.scanner :as scanner]
   [clj-watson.entrypoint :as entrypoint]
   [clj-watson.test-util :as tu]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(defn- exec [opts]
  (tu/with-out-capture
    (entrypoint/scan opts)))

(defn mocked-scan [& _]
  {:deps-scanned 1
   :findings [{:dependency "a/b"
               :mvn/version "1.2.3"
               :vulnerabilities [{:advisory {:severity "High"
                                             :cvss  {:score 7.8 :version "3.1"}}}]}]})

(deftest throws-on-cli-usage-error
  (is (match? {:out-lines (m/embeds [#"\* ERROR:"
                                     "ARG USAGE:"])
               :result {:ex {:cause "usage error"}}}
              (exec {:bad-opt :foo}))))

(deftest doesnt-throw-on-help-request
  (is (match? {:out-lines (m/via #(take 3 %) ["clj-watson"
                                              ""
                                              "ARG USAGE:"])
               :result {:exit 0}}
              (exec {:help true}))))

(deftest throws-on-missing-dependency-check-nvd-api-key
  (tu/with-log-capture [logged-lines {}]
    (with-redefs [scanner/set-watson-env-vars-as-properties (fn [& _])
                  scanner/get-nvd-api-key (fn [_])]
      (is (match? {:result {:ex {:cause "usage error"}}}
                  (exec {:deps-edn-path "deps.edn"})))
      (is (match? [[:fatal (m/embeds [#"cannot.* recommend.* without.* nvd.api.key"
                                      #"insist.* rerun.* :run-without-nvd-api-key"])]]
                  @logged-lines)))))

(deftest allows-run-without-nvd-api-key
  (tu/with-log-capture [logged-lines {}]
    (with-redefs [scanner/set-watson-env-vars-as-properties (fn [& _])
                  scanner/get-nvd-api-key (fn [_])
                  scanner/scan (fn [& _] {:deps-scanned 0
                                          :findings []})]
      (is (match? {:result nil}
                  (exec {:deps-edn-path "deps.edn" :run-without-nvd-api-key true})))
      (is (match? [[:warn (m/embeds [#"cannot.* recommend.* without.* nvd.api.key"
                                     #"opted to ignore.* :run-without-nvd-api-key"])]]
                  @logged-lines)))))

(deftest doesnt-throw-when-findings
  (with-redefs [entrypoint/scan* mocked-scan]
    (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                       "Vulnerable dependencies found: 1 (1 High)"])
                 :result nil}
                (exec {:deps-edn-path "deps.edn"})))))

(deftest throws-on-fail-request-when-findings
  (with-redefs [entrypoint/scan* mocked-scan]
    (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                       "Vulnerable dependencies found: 1 (1 High)"])
                 :result {:ex {:cause "fail-on-result requested and met"}}}
                (exec {:deps-edn-path "deps.edn" :fail-on-result true})))))

(deftest throws-on-cvss-threshold-breach-when-findings
  (with-redefs [entrypoint/scan* mocked-scan]
    (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                       "Vulnerable dependencies found: 1 (1 High)"
                                       #"CVSS fail score threshold of 7.8 met"
                                       #" a/b +1.2.3 +7.8 +\(version 3.1\)"])
                 :result {:ex {:cause "cvss-fail-threshold requested and met"}}}
                (exec {:deps-edn-path "deps.edn" :cvss-fail-threshold 7.8})))))

(deftest doesnt-throw-when-under-cvss-threshold
  (with-redefs [entrypoint/scan* mocked-scan]
    (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                       "Vulnerable dependencies found: 1 (1 High)"
                                       "No scores met CVSS fail threshold of 8.0"])
                 :result nil}
                (exec {:deps-edn-path "deps.edn" :cvss-fail-threshold 8})))))

