(ns clj-watson.diplomat.remediate
  (:require
    [clj-watson.diplomat.dependency :as diplomat.dependency]
    [clojure.edn :as edn]
    [clojure.tools.deps.alpha :as deps]
    [version-clj.core :as version]))

(def ^:private default-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(defn ^:private dependency-safe-versions [{:keys [vulnerabilities]}]
  (let [safe-versions (map (comp set :safe-versions) vulnerabilities)
        safe-versions-intersection (apply clojure.set/intersection safe-versions)]
    (version/version-sort safe-versions-intersection)))

(defn ^:private parent-dependency-contains-child-version?
  [parent-dependency-name parent-dependency-version child-dependency-name child-dependency-version repositories]
  (let [deps (assoc repositories :deps {parent-dependency-name {:mvn/version parent-dependency-version}})]
    (try
      (some-> deps
              (deps/calc-basis {})
              :libs
              (get child-dependency-name)
              :mvn/version
              (version/newer-or-equal? child-dependency-version))
      (catch Exception e
        (binding [*out* *err*]
          (println parent-dependency-name parent-dependency-version child-dependency-name child-dependency-version repositories)
          (println (ex-message e)))))))

(defn ^:private find-bump-version-using-latest [{:keys [parents dependency-name] :as vulnerability} repositories]
  (let [parents (-> parents first reverse)
        root-dependency (last parents)
        safe-version (-> vulnerability dependency-safe-versions first)]
    (if safe-version
      (loop [parents parents
             child-dependency dependency-name
             child-safe-version safe-version]
        (if (seq parents)
          (let [parent-dependency-name (first parents)
                latest-version (-> parent-dependency-name
                                   (diplomat.dependency/get-all-versions repositories)
                                   last)]
            (if (parent-dependency-contains-child-version? parent-dependency-name latest-version child-dependency child-safe-version repositories)
              (recur (next parents) parent-dependency-name latest-version)
              {root-dependency  {:exclusions [child-dependency]}
               child-dependency {:mvn/version child-safe-version}}))
          {child-dependency {:mvn/version child-safe-version}}))
      "vulnerability without patch.")))

(defn vulnerabilities-fix-suggestions [{:keys [vulnerable-dependencies] :as dependencies} deps-edn-path]
  (let [deps (-> deps-edn-path slurp edn/read-string)
        repositories (or (some->> deps :mvn/repos (assoc {} :mvn/repos)) default-repositories)
        vulnerable-dependencies-with-suggestions (map (fn [vulnerability]
                                                        (let [suggestion (find-bump-version-using-latest vulnerability repositories)]
                                                          (assoc vulnerability :fix-suggestion suggestion)))
                                                      vulnerable-dependencies)]
    (assoc dependencies :vulnerable-dependencies vulnerable-dependencies-with-suggestions)))
*e