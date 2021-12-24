(ns clj-watson.main
  (:require [clojure.tools.deps.alpha :as deps]
            [clojure.java.io :as io]
            [clj-watson.vulnerabilities :as watson.vulnerabilities]
            [clojure.string :as string])
  (:import (java.io File)
           (org.owasp.dependencycheck Engine)
           (org.owasp.dependencycheck.utils Settings)
           (org.owasp.dependencycheck.dependency Dependency)))

; it will be useful
; (api/find-versions '{:lib io.github.clojure/tools.build})

(def ^:private maven-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://clojars.org/repo"}})

(defn ^:private scan-dependency [dependency-path ^Engine engine]
  (->> dependency-path
       io/file
       (.scan engine))
  engine)

(defn ^:private scan-dependencies [dependencies engine]
  (reduce-kv (fn [engine _ {:keys [paths]}]
               (scan-dependency (first paths) engine))
             engine dependencies)
  (.analyzeDependencies engine)
  (.getDependencies engine))

(defn ^:private vulnerabilities-from-scanned-dependency [all-vulnerabilities ^Dependency dependency]
  (when-some [package-name (some-> dependency .getName (string/replace #":" "/") symbol)]
    (let [vulnerabilities (->> dependency .getVulnerabilities (mapv watson.vulnerabilities/get-details))]
      (assoc all-vulnerabilities package-name {:vulnerabilities vulnerabilities}))))

(defn ^:private update-maven-repositories-to-deps [deps-map]
  (update deps-map :mvn/repos merge maven-repositories))

(defn ^:private dependencies-from-deps [^String deps-path]
  (-> deps-path
      File.
      deps/slurp-deps
      update-maven-repositories-to-deps
      (deps/resolve-deps {:trace true})))

(defn ^:private build-engine []
  (let [settings (Settings.)
        engine (Engine. settings)]
    engine))

(defn scan [deps-path]
  (let [project-dependencies (dependencies-from-deps deps-path)
        engine (build-engine)
        exercised-engine (scan-dependencies project-dependencies engine)
        dependencies-with-vulnerabilities (reduce vulnerabilities-from-scanned-dependency {} exercised-engine)]
    (merge-with merge project-dependencies dependencies-with-vulnerabilities)))

(comment
  (scan "resources/vulnerable-deps.edn"))
