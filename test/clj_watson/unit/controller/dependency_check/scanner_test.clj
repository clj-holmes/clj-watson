(ns clj-watson.unit.controller.dependency-check.scanner-test
  (:require
   [babashka.fs :as fs]
   [clj-watson.controller.dependency-check.scanner :as scanner]
   [clj-watson.logic.utils :as utils]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [matcher-combinators.matchers :as m]
   [matcher-combinators.test]))

(def work-dir "target/create-settings-test")

(deftest create-settings-test
  ;; To truly test that settings are applied as expected, we need to spawn a process to
  ;; set Java system properties and a environment variables.
  ;; This is slow, so we are careful to limit the number of spawns.
  ;;
  ;; Precedence highest to lowest should be:
  ;; :sys            - system property specified on command line
  ;; :env-var        - environment variable
  ;; :watson-user    - clj-watson clj-watson.properties
  ;; :watson-default - clj-watson dependency-check.properties
  ;; :dc-default     - dependency-check property default

  (fs/delete-tree work-dir)
  (fs/create-dirs work-dir)
  (let [dc-defaults (->> (utils/load-properties (io/resource "dependencycheck.properties"))
                         (into {}))
        ;; Grab some actual valid properties from dependency check, update as necessary if properties change
                   ;; prop name         expected-winner  applied in (:dc-default-prop defines expectation)
        properties [["data.driver_name" :sys             #{:sys :env-var :watson-user :watson-default :dc-default}]
                    ["cpe.url"          :env-var         #{:env-var :watson-user :watson-default :dc-default}]
                    ;; multiple watson user props to test output value occlusion
                    ["data.password"    :watson-user     #{:watson-user :watson-default :dc-default}]
                    ["nvd.api.key"      :watson-user     #{:watson-user}] ;; nvd.api.key is not a dc default prop
                    ["data.user"        :watson-user     #{:watson-user :watson-default :dc-default}]
                    ["data.version"     :watson-user     #{:watson-user                 :dc-default}]
                    ["data.file_name"   :watson-default  #{:watson-default :dc-default}]
                    ["odc.autoupdate"   :dc-default      #{:dc-default}]]

        in-settings (reduce (fn [acc [prop-name _ apply-in]]
                              (let [v (get dc-defaults prop-name :not-set)]
                                (cond
                                  (and (apply-in :dc-default) (= :not-set v))
                                  (throw (ex-info (format "pre-check failed: expected %s to be a dependency-check default" prop-name) {}))

                                  (and (not (apply-in :dc-default)) (not= :not-set v))
                                  (throw (ex-info (format "pre-check failed: expected %s NOT to be a dependency-check default" prop-name) {})))

                                (cond-> acc
                                  (apply-in :sys)
                                  (update :sys conj (str "-J-D" prop-name "=" ":sys:-" v))

                                  (apply-in :env-var)
                                  (update :env-var assoc
                                          (str "CLJ_WATSON_" (-> prop-name
                                                                 (str/replace "_" "__")
                                                                 (str/replace "." "_")
                                                                 (str/upper-case)))
                                          (str ":env-var:-" v))

                                  (apply-in :watson-user)
                                  (update :watson-user conj
                                          (str prop-name "=" ":watson-user:-" v))

                                  (apply-in :watson-default)
                                  (update :watson-default conj
                                          (str prop-name "=" ":watson-default:-" v)))))
                            {:sys []
                             :env-var {}
                             :watson-user []
                             :watson-default []}
                            properties)
        watson-default (->> in-settings :watson-default (str/join "\n"))
        watson-user (->> in-settings :watson-user (str/join "\n"))
        sys-env-vars (->> (System/getenv)
                          (reduce-kv (fn [m k v]
                                       (if (str/starts-with? k "CLJ_WATSON_")
                                         m
                                         (assoc m k v)))
                                     {}))
        env-vars (merge sys-env-vars (:env-var in-settings))
        watson-default-file (str (fs/file work-dir "dependency-check.properties"))
        watson-user-file (str (fs/file work-dir "clj-watson.properties"))
        report-on-props (mapv first properties)]

    (spit watson-default-file watson-default)
    (spit watson-user-file watson-user)

    (doseq [{:keys [args desc]} [{:desc "with explicit filenames"
                                  :args (conj (into ["clojure"] (:sys in-settings))
                                              "-X:test:create-settings-test"
                                              ":watson-default-file" watson-default-file
                                              ":watson-user-file" watson-user-file
                                              ":report-on-props" (pr-str report-on-props))}
                                 {:desc "with files found on classpath"
                                  :args (conj (into ["clojure"] (:sys in-settings))
                                              "-X:test:create-settings-test:create-settings-test-resources"
                                              ":report-on-props" (pr-str report-on-props))}]]

      (testing desc
        (println "-can we launch clojure?->" (:out (shell/sh "clojure" "-Sdescribe")))
        (println "-desc->" desc)
        (println "-args->" (pr-str args))
        (let [{:keys [out exit]} (apply shell/sh (conj args #_#_:env env-vars))
              expected-settings (reduce (fn [acc [prop-name expected-winner]]
                                          (let [v (get dc-defaults prop-name :not-set)]
                                            (assoc acc prop-name
                                                   (if (= :dc-default expected-winner)
                                                     v
                                                     (str expected-winner ":-" v)))))
                                        {}
                                        properties)]
          (is (zero? exit))
          (is (match? (m/equals expected-settings) (edn/read-string (slurp (fs/file work-dir "result.edn"))))
              "resulting settings")
          (is (match? [""
                       "Reading 5 dependency-check properties from:"
                       #" .*/clj-watson/target/create-settings-test/dependency-check.properties"
                       "Merging 6 additional clj-watson properties from:"
                       #" .*/clj-watson/target/create-settings-test/clj-watson.properties"
                       #"  cpe.url=:watson-user:-.+"
                       #"  data.driver_name=:watson-user:-.+"
                       "  data.password=***OCCLUDED***"
                       "  data.user=***OCCLUDED***"
                       #"  data.version=:watson-user:-.+"
                       "  nvd.api.key=***OCCLUDED***"
                       ""
                       "Setting cpe.url from CLJ_WATSON_CPE_URL."
                       "Ignoring CLJ_WATSON_DATA_DRIVER__NAME as data.driver_name is already set."]
                      (str/split-lines out))
              "stdout"))))))

;; -X exec entrypoint for testing only
(defn create-settings-test-entrypoint [{:keys [watson-default-file watson-user-file report-on-props]}]
  (let [watson-default-file (when watson-default-file (str watson-default-file))
        watson-user-file (when watson-user-file (str watson-user-file))]
    (fs/create-dirs work-dir)
    (let [settings (#'scanner/create-settings watson-default-file watson-user-file)]
      (spit (fs/file work-dir "result.edn") (pr-str (reduce (fn [acc n]
                                                              (assoc acc n (.getString settings n)))
                                                            {}
                                                            report-on-props))))))
