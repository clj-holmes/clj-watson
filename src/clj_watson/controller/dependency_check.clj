(ns clj-watson.controller.dependency-check
  (:require
   [clj-watson.diplomat.dependency-check :as diplomat.dependency-check]
   [clj-watson.diplomat.deps :as diplomat.deps]
   [clj-watson.logic.utils :as logic.utils])
  (:import
   (java.io File)
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
    (when properties-file-path
      (->> properties-file-path File. (.mergeProperties settings)))
    settings))

(defn ^:private build-engine [^String properties-file-path]
  (let [settings (create-settings properties-file-path)]
    (Engine. settings)))

(defn ^:private prepare-environment [deps-edn-path dependency-check-properties]
  (let [{:keys [dependencies project-deps]} (diplomat.deps/read-and-resolve deps-edn-path)
        engine (build-engine dependency-check-properties)]
    (diplomat.dependency-check/update-download-database engine)
    {:project/dependencies dependencies :project/deps project-deps :dependency-check/engine engine}))

(defn scan-dependencies [deps-edn-path properties-file-path]
  (let [environment (prepare-environment deps-edn-path properties-file-path)
        engine (scan-jars environment)
        dependency-check-dependencies (->> engine .getDependencies Arrays/asList)]
    (-> environment
        (assoc :dependency-check/engine engine)
        (assoc :dependency-check/dependencies dependency-check-dependencies))))