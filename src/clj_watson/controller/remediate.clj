(ns clj-watson.controller.remediate
  (:require [clj-watson.diplomat.dependency :as diplomat.dependency]
            [version-clj.core :as version]
            [clojure.tools.deps.alpha.util.maven :as maven]))

(defn ^:private parent-contains-child-version?
  [parent-dependency parent-version child-dependency child-version repositories]
  (let [deps (assoc repositories :deps {parent-dependency {:mvn/version parent-version}})]
    (some-> deps
            diplomat.dependency/resolve-dependency!
            :libs
            (get child-dependency) :mvn/version
            (version/newer-or-equal? child-version))))

(defn ^:private secure-dependency-tree-suggestion [{:keys [parents dependency secure-version]} repositories]
  (let [parents (-> parents first reverse)
        root-dependency (or (last parents) dependency)]
    (if (seq parents)
      (loop [parents parents
             child-dependency dependency
             child-version secure-version]
        (if (seq parents)
          (let [parent-dependency (first parents)
                parent-version (last (diplomat.dependency/get-all-versions! parent-dependency repositories))]
            (if (parent-contains-child-version? parent-dependency parent-version child-dependency child-version repositories)
              (recur (next parents) parent-dependency parent-version)
              {root-dependency  {:exclusions [child-dependency]}
               child-dependency {:mvn/version child-version}}))
          {child-dependency {:mvn/version child-version}}))
      (do
        {dependency {:mvn/version secure-version}}))))

(defn scan [vulnerable-dependencies deps]
  (map (fn [vulnerable-dependency]
         (let [repositories (select-keys deps [:mvn/repos])
               suggestion (secure-dependency-tree-suggestion vulnerable-dependency repositories)]
           (assoc vulnerable-dependency :remediate-suggestion suggestion)))
       vulnerable-dependencies))

(comment
  (def vulnerable-dependencies
    '[{:mvn/version     "2.14.0",
       :deps/manifest   :mvn,
       :dependents      [com.taoensso/carmine],
       :parents         #{[io.replikativ/datahike io.replikativ/hitchhiker-tree com.taoensso/carmine]},
       :paths           ["/Users/dpr/.m2/repository/com/taoensso/nippy/2.14.0/nippy-2.14.0.jar"],
       :dependency      com.taoensso/nippy,
       :vulnerabilities [{:package                {:name "com.taoensso:nippy", :ecosystem "MAVEN"},
                          :vulnerableVersionRange "< 2.14.2",
                          :advisory               {:severity    "HIGH",
                                                   :ghsaId      "GHSA-p5gm-fgfx-hr7h",
                                                   :cvss        {:score 7.8},
                                                   :description "A deserialization flaw is present in Taoensso Nippy before 2.14.2. In some circumstances, it is possible for an attacker to create a malicious payload that, when deserialized, will allow arbitrary code to be executed. This occurs because there is automatic use of the Java Serializable interface.",
                                                   :identifiers [{:value "GHSA-p5gm-fgfx-hr7h"} {:value "CVE-2020-24164"}],
                                                   :publishedAt "2022-02-10T20:55:10Z",
                                                   :origin      "UNSPECIFIED"},
                          :firstPatchedVersion    {:identifier "2.14.2"}}],
       :secure-version  "2.14.2"}])
  (scan vulnerable-dependencies {:mvn/repos maven/standard-repos}))