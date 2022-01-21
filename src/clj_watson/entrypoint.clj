(ns clj-watson.entrypoint
  (:gen-class)
  (:require
   [clj-watson.report :as watson.report]
   [clj-watson.vulnerabilities :as watson.vulnerabilities]
   [clojure.string :as string]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.extensions :as ext])
  (:import
   (java.io File)
   (java.util Arrays)
   (org.owasp.dependencycheck Engine)
   (org.owasp.dependencycheck.dependency Dependency)
   (org.owasp.dependencycheck.utils Settings)))

(def ^:private maven-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://clojars.org/repo"}})

(defn ^:private get-latest-version [dependency project-deps]
  (some-> dependency
          (ext/find-all-versions nil project-deps)
          last
          :mvn/version))

(defn ^:private clojure-file? [dependency-path]
  (string/ends-with? dependency-path ".jar"))

(defn ^:private scan-dependencies [dependencies engine]
  (doseq [{:keys [paths]} (vals dependencies)]
    (when (clojure-file? (first paths))
      (->> paths first (.scan engine))))
  (.analyzeDependencies engine)
  (Arrays/asList (.getDependencies engine)))

(defn ^:private vulnerabilities-from-scanned-dependency [project-deps dependencies ^Dependency dependency]
  (when-let [dependency-name (some-> (.getName dependency) (string/replace #":" "/") symbol)]
    (let [dependency-map (dependency-name dependencies)
          latest-version (get-latest-version dependency-name project-deps)
          vulnerabilities (->> dependency
                               .getVulnerabilities
                               (mapv (partial watson.vulnerabilities/get-details (:mvn/version dependency-map)))
                               (filter identity))]
      (when (seq vulnerabilities)
        (-> dependency-map
            (assoc :vulnerabilities vulnerabilities)
            (assoc :dependency-name dependency-name)
            (assoc :latest-version latest-version))))))

(defn ^:private update-maven-repositories-to-deps [deps-map]
  (update deps-map :mvn/repos merge maven-repositories))

(defn ^:private dependencies-from-deps [^String deps-path]
  (-> deps-path
      File.
      deps/slurp-deps
      update-maven-repositories-to-deps))

(defn ^:private build-engine [^String properties-file-path]
  (let [settings (Settings.)
        _ (when properties-file-path
            (.mergeProperties settings (File. properties-file-path)))
        engine (Engine. settings)]
    engine))

(defn ^:private download-database [engine]
  (println "Downloading/Updating database.")
  (.doUpdates engine)
  (println "Download/Update completed."))

(defn -main [deps-path]
  (let [project-deps (dependencies-from-deps deps-path)
        dependencies (deps/resolve-deps project-deps {:trace true})
        engine (build-engine "resources/dependency-check.properties")
        _ (download-database engine)
        exercised-engine (scan-dependencies dependencies engine)
        parse-vulnerabilities (partial vulnerabilities-from-scanned-dependency project-deps dependencies)]
    (->> exercised-engine
         (pmap parse-vulnerabilities)
         (filter identity)
         watson.report/generate
         println))
  (shutdown-agents))

(comment
  (def result (-main "deps.edn"))
  (def result (-main "resources/vulnerable-deps.edn")))