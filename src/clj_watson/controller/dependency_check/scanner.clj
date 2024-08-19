(ns clj-watson.controller.dependency-check.scanner
  (:require
   [clj-watson.cli-spec :as cli-spec]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as log])
  (:import
   (java.io ByteArrayInputStream File)
   (java.util Arrays)
   (org.owasp.dependencycheck Engine)
   (org.owasp.dependencycheck.utils Settings)))

(defn- sanitize-property
  "Given a line from a properties file, remove sensitive information."
  [line]
  (string/replace line
                  #"(key=[-a-fA-F0-9]{4}).*([-a-fA-F0-9]{4})"
                  "$1****-****-****-****-********$2"))

(comment
  ;; ensure (fake) API key is redacted:
  (sanitize-property "nvd.api.key=72a48765-90ab-5678-abcd-1234abcd5489"))

(defn ^:private env-var->property [env-var]
  (-> env-var
      (string/replace #"^CLJ_WATSON_" "") ; strip prefix
      (string/lower-case)                 ; lowercase
      (string/replace "_" ".")            ; _ -> .
      (string/replace ".." "_")))         ; allow __ -> .. -> _

(comment
  (env-var->property "CLJ_WATSON_NVD_API_KEY")
  (env-var->property "CLJ_WATSON_DATA_FILE__NAME")
  )

(defn ^:private set-watson-env-vars-as-properties []
  (run! (fn [[env-var value]]
          (when (string/starts-with? env-var "CLJ_WATSON_")
            (let [property (env-var->property env-var)
                  p-value  (System/getProperty property)]
              (if p-value
                (println (str "Ignoring " env-var " as " property " is already set."))
                (do
                  (println (str "Setting " property " from " env-var "."))
                  (System/setProperty property value))))))
        (System/getenv))
  (println))

(defn ^:private create-settings [^String properties-file-path ^String additional-properties-file-path]
  (let [settings (Settings.)
        props
        (if properties-file-path
          (->> properties-file-path File.)
          (->> "dependency-check.properties" io/resource))]
    (println (str "\nRead " (count (line-seq (io/reader props))) " dependency-check properties."))
    (if properties-file-path
      (->> props (.mergeProperties settings))
      (->> props slurp .getBytes ByteArrayInputStream. (.mergeProperties settings)))
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
      (println "No additional properties found.\n"))
    (set-watson-env-vars-as-properties)
    settings))

(defn ^:private validate-settings
  "Validate settings, logging any findings.
  Returns {:exit <code>} when app should exit with exit <code>, else nil"
  [settings {:keys [run-without-nvd-api-key] :as opts}]
  (when (not (or (.getString settings "nvd.api.key") (System/getProperty "nvd.api.key")))
    (if run-without-nvd-api-key
      (log/warn (format (str "We cannot recommend running without an nvd.api.key specified.\n"
                             "   You have opted to ignore this advice via the %s option.\n"
                             "   Expect slow NVD data updates and downloads.\n")
                        (cli-spec/styled-long-opt :run-without-nvd-api-key opts)))
      (do (log/fatal (format (str "We cannot recommend running without an nvd.api.key specified.\n"
                                  "   If you insist, rerun with the %s option, but be warned\n"
                                  "   that you will experience slow NVD data updates and downloads.")
                             (cli-spec/styled-long-opt :run-without-nvd-api-key opts)))
          {:exit 1}))))

(defn ^:private build-engine [settings]
  (Engine. settings))

(defn ^:private clojure-file? [dependency-path]
  (string/ends-with? dependency-path ".jar"))

(defn ^:private scan-jars [engine dependencies]
  (->> dependencies
       (map :paths)
       (apply concat)
       (filter clojure-file?)
       (map io/file)
       (.scan engine))
  (.analyzeDependencies engine)
  engine)

(defn start!
  [dependencies dependency-check-properties clj-watson-properties opts]
  (let [settings (create-settings dependency-check-properties clj-watson-properties)]
    (when-let [{:keys [exit]} (validate-settings settings opts)]
      (System/exit exit))
    (with-open [engine (build-engine settings)]
      (-> engine
          (scan-jars dependencies)
          (.getDependencies)
          (Arrays/asList)))))
