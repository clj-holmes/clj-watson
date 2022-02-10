(ns clj-watson.diplomat.remediate
  (:require
   [clj-watson.diplomat.dependency :as diplomat.dependency]
   [clojure.tools.deps.alpha :as deps]
   [version-clj.core :as version]))

(defn ^:private dependency-safe-versions [{:keys [vulnerabilities]}]
  (let [safe-versions (map (comp set :safe-versions) vulnerabilities)
        safe-versions-intersection (apply clojure.set/intersection safe-versions)]
    (version/version-sort safe-versions-intersection)))

(defn ^:private parent-dependency-contains-child-version?
  [parent-dependency-name parent-dependency-version child-dependency-name child-dependency-version repositories]
  (let [deps {:mvn/repos repositories
              :deps      {parent-dependency-name {:mvn/version parent-dependency-version}}}]
    (some-> deps
            (deps/calc-basis {})
            :libs
            (get child-dependency-name)
            :mvn/version
            (version/newer-or-equal? child-dependency-version))))

(defn ^:private find-bump-version-using-latest [{:keys [parents dependency-name] :as vulnerability} repositories]
  (let [parents (-> parents first reverse)
        root-dependency (last parents)
        safe-version (-> vulnerability dependency-safe-versions first)]
    (loop [parents parents
           child-dependency dependency-name
           child-safe-version safe-version]
      (if (seq parents)
        (let [parent-dependency-name (first parents)
              latest-version (->> parent-dependency-name
                                  diplomat.dependency/get-all-versions
                                  last)]
          (if (parent-dependency-contains-child-version? parent-dependency-name latest-version child-dependency child-safe-version repositories)
            (recur (next parents) parent-dependency-name latest-version)
            {root-dependency {:exclusions [child-dependency]}
             child-dependency       {:mvn/version child-safe-version}}))
        {child-dependency {:mvn/version child-safe-version}}))))

(defn vulnerabilities-fix-suggestions [{:keys [vulnerable-dependencies deps]}]
  (let [repositories (:mvn/repos deps)]
    (map #(find-bump-version-using-latest % repositories) vulnerable-dependencies)))