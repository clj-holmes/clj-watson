(ns clj-watson.main
  (:require [clojure.tools.deps.alpha :as deps]
            [clojure.java.io :as io]
            [clj-watson.vulnerabilities :as watson.vulnerabilities]
            [clojure.string :as string])
  (:import (java.io File)
           (java.util Arrays)
           (org.owasp.dependencycheck Engine)
           (org.owasp.dependencycheck.utils Settings)
           (org.owasp.dependencycheck.dependency Dependency)))

; it will be useful
; (api/find-versions '{:lib io.github.clojure/tools.build})

(def ^:private maven-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://clojars.org/repo"}})

(defn clojure-file? [dependency-path]
  (string/ends-with? dependency-path ".jar"))

(defn ^:private scan-dependencies [dependencies engine]
  (doseq [{:keys [paths]} (vals dependencies)]
    (when (clojure-file? (first paths))
      (->> paths first (.scan engine))))
  (.analyzeDependencies engine)
  (Arrays/asList (.getDependencies engine)))

(defn ^:private vulnerabilities-from-scanned-dependency [all-vulnerabilities ^Dependency dependency]
  (if-let [dependency-name (some-> (.getName dependency) (string/replace #":" "/") symbol) ]
    (let [vulnerabilities (->> dependency .getVulnerabilities (mapv watson.vulnerabilities/get-details))]
      (assoc all-vulnerabilities dependency-name {:vulnerabilities vulnerabilities}))
    all-vulnerabilities))

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
  (def result (scan "resources/vulnerable-deps.edn"))

  (reduce-kv (fn [dependencies dependency-name dependency-map]
               (if (-> dependency-map :vulnerabilities seq)
                 (assoc dependencies dependency-name dependency-map)
                 dependencies))
             {} result))