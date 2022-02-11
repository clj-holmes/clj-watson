(ns clj-watson.controller.dependency-check
  (:require
   [clj-watson.diplomat.dependency-check :as diplomat.dependency-check]
   [clj-watson.diplomat.deps :as diplomat.deps]
   [clj-watson.logic.utils :as logic.utils]
   [clojure.java.io :as io])
  (:import
   (java.io File ByteArrayInputStream)
   (java.util Arrays)
   (org.owasp.dependencycheck Engine)
   (org.owasp.dependencycheck.utils Settings)))

(defn ^:private scan-jars [{:dependency-check/keys [engine]
                            :project/keys [dependencies]}]
  (doseq [{:keys [paths]} (vals dependencies)]
    (let [path (first paths)]
      (when (-> path logic.utils/clojure-file?)
        (.scan engine path))))
  (.analyzeDependencies engine)
  engine)

(defn ^:private create-settings [^String properties-file-path]
  (let [settings (Settings.)]
    (if properties-file-path
      (->> properties-file-path File. (.mergeProperties settings))
      (->> "dependency-check.properties" io/resource slurp .getBytes ByteArrayInputStream. (.mergeProperties settings)))
    settings))

(defn ^:private build-engine [^String properties-file-path]
  (let [settings (create-settings properties-file-path)]
    (Engine. settings)))

(defn ^:private prepare-environment [deps-edn-path dependency-check-properties aliases]
  (let [{:keys [dependencies project-deps]} (diplomat.deps/read-and-resolve deps-edn-path aliases)
        engine (build-engine dependency-check-properties)]
    (diplomat.dependency-check/update-download-database engine)
    {:project/dependencies dependencies :project/deps project-deps :dependency-check/engine engine}))

(defn scan-dependencies [deps-edn-path properties-file-path aliases]
  (let [environment (prepare-environment deps-edn-path properties-file-path aliases)
        engine (scan-jars environment)
        dependency-check-dependencies (->> engine .getDependencies Arrays/asList)]
    (-> environment
        (assoc :dependency-check/engine engine)
        (assoc :dependency-check/dependencies dependency-check-dependencies))))