(ns clj-watson.controller.remediate
  (:require [clj-watson.diplomat.dependency :as diplomat.dependency]
            [version-clj.core :as version]
            [clojure.tools.deps.alpha.util.maven :as maven]))

(defn ^:private parent-contains-child-version?
  [parent-dependency parent-version child-dependency child-version deps]
  (let [deps (assoc deps :deps {parent-dependency {:mvn/version parent-version}})]
    (some-> deps
            diplomat.dependency/resolve-dependency!
            :libs
            (get child-dependency) :mvn/version
            (version/newer-or-equal? child-version))))

(defn ^:private secure-dependency-tree-suggestion [{:keys [parents dependency secure-version]} deps]
  (let [parents (-> parents first reverse)
        repositories (select-keys deps [:mvn/repos])
        root-dependency (or (last parents) dependency)]
    (if (seq parents)
      (loop [parents parents
             child-dependency dependency
             child-version secure-version]
        (if (seq parents)
          (let [parent-dependency (first parents)
                parent-version (last (diplomat.dependency/get-all-versions! parent-dependency repositories))]
            (if (parent-contains-child-version? parent-dependency parent-version child-dependency child-version deps)
              (recur (next parents) parent-dependency parent-version)
              ; secure version not found in dependency chain
              {root-dependency  {:exclusions [child-dependency]}
               child-dependency {:mvn/version child-version}}))
          ; secure version find in all dependency chain
          {child-dependency {:mvn/version child-version}}))
      (do
        ; since it is a direct dependency just bump it to the previously secure-version find.
        {dependency {:mvn/version secure-version}}))))

(defn ^:private scan* [vulnerable-dependency deps]
  (let [suggestion (secure-dependency-tree-suggestion vulnerable-dependency deps)]
    (assoc vulnerable-dependency :remediate-suggestion suggestion)))

(defn scan [vulnerable-dependencies deps]
  (map #(scan* % deps) vulnerable-dependencies))

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
                                                   :identifiers [{:value "GHSA-p5gm-fgfx-hr7h"} {:value "CVE-2020-24164"}],
                                                   :publishedAt "2022-02-10T20:55:10Z",
                                                   :origin      "UNSPECIFIED"},
                          :firstPatchedVersion    {:identifier "2.14.2"}}],
       :secure-version  "2.14.2"}])
  (scan vulnerable-dependencies {:mvn/repos maven/standard-repos}))