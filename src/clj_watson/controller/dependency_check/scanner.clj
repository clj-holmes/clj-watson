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

(defn- sanitize-property
  "Given a line from a properties file, remove sensitive information."
  [line]
  (string/replace line
                  #"(key=[-a-fA-F0-9]{4}).*([-a-fA-F0-9]{4})"
                  "$1****-****-****-****-********$2"))

(comment
  ;; ensure (fake) API key is redacted:
  (sanitize-property "nvd.api.key=72a48765-90ab-5678-abcd-1234abcd5489"))

(defn ^:private create-settings [^String properties-file-path ^String additional-properties-file-path]
  (let [settings (Settings.)]
    (let [props
          (if properties-file-path
            (->> properties-file-path File.)
            (->> "dependency-check.properties" io/resource))]
      (println (str "\nRead " (count (line-seq (io/reader props))) " dependency-check properties."))
      (if properties-file-path
        (->> props (.mergeProperties settings))
        (->> props slurp .getBytes ByteArrayInputStream. (.mergeProperties settings))))
    (if-let [add-props
             (if additional-properties-file-path
               (->> additional-properties-file-path File.)
               (some->> "clj-watson.properties" io/resource))]
      (do
        (println "Merging additional properties:")
        (try
          (doseq [line (line-seq (io/reader add-props))]
            (println (str "  " (sanitize-property line))))
          (catch Exception e
            (println (str "Unable to read: "
                          (or additional-properties-file-path "clj-watson.properties")
                          " due to: "
                          (ex-message e)))))
        (println "\n")
        (if additional-properties-file-path
          (->> add-props (.mergeProperties settings))
          (some->> add-props slurp .getBytes ByteArrayInputStream. (.mergeProperties settings))))
      (println "No additional properties found."))
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
