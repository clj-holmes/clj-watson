(ns clj-watson.controller.dependency-check.scanner
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (java.io File ByteArrayInputStream)
   (java.util Arrays)
   (org.owasp.dependencycheck Engine)
   (org.owasp.dependencycheck.utils Settings)))

(defn ^:private update-download-database [engine]
  (binding [*out* *err*]
    (println "Downloading/Updating database.")
    (.doUpdates engine)
    (println "Download/Update completed.")))

(defn ^:private create-settings [^String properties-file-path ^String additional-properties-file-path]
  (let [settings (Settings.)]
    (if properties-file-path
      (->> properties-file-path File. (.mergeProperties settings))
      (->> "dependency-check.properties" io/resource slurp .getBytes ByteArrayInputStream. (.mergeProperties settings)))
    (when additional-properties-file-path
      (->> additional-properties-file-path File. (.mergeProperties settings))
      (some->> "clj-watson.properties" io/resource slurp .getBytes ByteArrayInputStream. (.mergeProperties settings)))
    settings))

(defn ^:private build-engine [dependency-check-properties clj-watson-properties]
  (let [settings (create-settings dependency-check-properties clj-watson-properties)
        engine (Engine. settings)]
    (update-download-database engine)
    engine))

(defn ^:private clojure-file? [dependency-path]
  (string/ends-with? dependency-path ".jar"))

(defn ^:private scan-jars [dependencies dependency-check-properties clj-watson-properties]
  (let [engine (build-engine dependency-check-properties clj-watson-properties)]
    (->> dependencies
         (map :paths)
         (apply concat)
         (filter clojure-file?)
         (map io/file)
         (.scan engine))
    (.analyzeDependencies engine)
    engine))

(defn start! [dependencies dependency-check-properties clj-watson-properties]
  (let [engine (scan-jars dependencies dependency-check-properties clj-watson-properties)
        scanned-dependencies (->> engine .getDependencies Arrays/asList)]
    scanned-dependencies))
