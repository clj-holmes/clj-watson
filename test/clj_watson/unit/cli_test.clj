(ns clj-watson.unit.cli-test
  "Sanity -M tests. Because scanning takes a very long time, we use mocks.
  See -X/-T exec variant in clj-watson.unit.entrypoint-test"
  (:require
   [clj-watson.cli :as cli]
   [clj-watson.controller.dependency-check.scanner :as scanner]
   [clj-watson.entrypoint :as entrypoint]
   [clj-watson.test-util :as tu]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(defn- main [& args]
  (tu/with-out-capture
    (apply cli/-main args)))

(defn mocked-scan [& _]
  {:deps-scanned 1
   :findings [{:dependency "a/b"
               :mvn/version "1.2.3"
               :vulnerabilities [{:advisory {:severity "High"
                                             :cvss  {:score 7.8 :version "3.1"}}}]}]})

(deftest exits-with-one-on-cli-usage-error
  (let [exit-code (atom :not-set)]
    (with-redefs [shutdown-agents (fn [])
                  cli/system-exit (fn [code] (reset! exit-code code))]
      (is (match? {:out-lines (m/embeds [#"\* ERROR:"
                                         "ARG USAGE:"])}
                  (main "scan" "--bad-opt")))
      (is (= 1 @exit-code)))))

(deftest exits-with-zero-on-help-request
  (let [exit-code (atom :not-set)]
    (with-redefs [shutdown-agents (fn [])
                  cli/system-exit (fn [code] (reset! exit-code code))]
      (is (match? {:out-lines (m/via #(take 3 %) ["clj-watson"
                                                  ""
                                                  "ARG USAGE:"])}
                  (main "scan" "--help")))
      (is (= 0 @exit-code)))))

(deftest exits-with-one-on-missing-dependency-check-nvd-api-key
  (let [exit-code (atom :not-set)]
    (tu/with-log-capture [logged-lines {}]
      (with-redefs [shutdown-agents (fn [])
                    cli/system-exit (fn [code] (reset! exit-code code))
                    scanner/set-watson-env-vars-as-properties (fn [& _])
                    scanner/get-nvd-api-key (fn [_])]
        (main "scan" "--deps-edn-path" "deps.edn")
        (is (match? [[:fatal (m/embeds [#"cannot.* recommend.* without.* nvd.api.key"
                                        #"insist.* rerun.* --run-without-nvd-api-key"])]]
                    @logged-lines))
        (is (= 1 @exit-code))))))

(deftest allows-run-without-nvd-api-key
  (let [exit-code (atom :not-set)]
    (tu/with-log-capture [logged-lines {}]
      (with-redefs [shutdown-agents (fn [])
                    cli/system-exit (fn [code] (reset! exit-code code))
                    scanner/get-nvd-api-key (fn [_])
                    scanner/scan (fn [& _] {:deps-scanned 0
                                            :findings []})]
        (is (match? {:result nil}
                    (main "scan" "--deps-edn-path" "deps.edn" "--run-without-nvd-api-key")))
        (is (match? [[:warn (m/embeds [#"cannot.* recommend.* without.* nvd.api.key"
                                       #"opted to ignore.* --run-without-nvd-api-key"])]]
                    @logged-lines))
        (is (= :not-set @exit-code))))))

(deftest exits-naturally-when-findings
  (let [exit-code (atom :not-set)]
    (with-redefs [shutdown-agents (fn [])
                  cli/system-exit (fn [code] (reset! exit-code code))
                  entrypoint/scan* mocked-scan]
      (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                         "Vulnerable dependencies found: 1 (1 High)"])}
                  (main "scan" "--deps-edn-path" "deps.edn")))
      (is (= :not-set @exit-code)))))

(deftest exits-with-one-on-fail-request-when-findings
  (let [exit-code (atom :not-set)]
    (with-redefs [shutdown-agents (fn [])
                  cli/system-exit (fn [code] (reset! exit-code code))
                  entrypoint/scan* mocked-scan]
      (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                         "Vulnerable dependencies found: 1 (1 High)"])}
                  (main "scan" "--deps-edn-path" "deps.edn" "--fail-on-result")))
      (is (= 1 @exit-code)))))

(deftest exits-with-one-on-cvss-threshold-breach-when-findings
  (let [exit-code (atom :not-set)]
    (with-redefs [shutdown-agents (fn [])
                  cli/system-exit (fn [code] (reset! exit-code code))
                  entrypoint/scan* mocked-scan]
      (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                         "Vulnerable dependencies found: 1 (1 High)"
                                         #"CVSS fail score threshold of 7.8 met"
                                         #" a/b +1.2.3 +7.8 +\(version 3.1\)"])}
                  (main "scan" "--deps-edn-path" "deps.edn" "--cvss-fail-threshold" "7.8")))
      (is (= 1 @exit-code)))))

(deftest exits-naturally-when-under-cvss-threshold
  (let [exit-code (atom :not-set)]
    (with-redefs [shutdown-agents (fn [])
                  cli/system-exit (fn [code] (reset! exit-code code))
                  entrypoint/scan* mocked-scan]
      (is (match? {:out-lines (m/embeds ["Dependencies scanned: 1"
                                         "Vulnerable dependencies found: 1 (1 High)"
                                         "No scores met CVSS fail threshold of 8.0"])}
                  (main "scan" "--deps-edn-path" "deps.edn" "--cvss-fail-threshold" "8")))
      (is (= :not-set @exit-code)))))

