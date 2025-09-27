(ns clj-watson.controller.dependency-check.scanner
  (:require
   [clj-watson.cli-spec :as cli-spec]
   [clj-watson.logic.utils :as utils]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.tools.logging :as log])
  (:import
   (java.nio.file Paths)
   (java.util Arrays)
   (org.owasp.dependencycheck Engine)
   (org.owasp.dependencycheck.utils Downloader Settings)))

(defn ^:private env-var->property [env-var]
  (-> env-var
      (string/replace #"^CLJ_WATSON_" "") ; strip prefix
      (string/lower-case)                 ; lowercase
      (string/replace "_" ".")            ; _ -> .
      (string/replace ".." "_")))         ; allow __ -> .. -> _

(comment
  (env-var->property "CLJ_WATSON_NVD_API_KEY")
  (env-var->property "CLJ_WATSON_DATA_FILE__NAME"))

;; mock target
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

(defn ^:private resource-as-file [resource]
  (when-let  [r (io/resource resource)]
    (-> r .toURI Paths/get .toFile)))

;; called from test
(defn ^:private create-settings [^String watson-default-props-file ^String watson-user-props-file]
  (let [settings (Settings.)
        default-props-file
        (if watson-default-props-file
          (io/file watson-default-props-file)
          (resource-as-file "dependency-check.properties"))
        num-props (count (utils/load-properties default-props-file))
        plural-props (fn [cnt] (if (= 1 cnt) "property" "properties"))]
    (println (str "\nReading " num-props " dependency-check "
                  (plural-props num-props)
                  " from:\n " (.getCanonicalPath default-props-file)))
    (with-open [in-stream (io/input-stream default-props-file)]
      (.mergeProperties settings in-stream))
    (if-let [user-props-file
             (if watson-user-props-file
               (->> watson-user-props-file io/file)
               (resource-as-file "clj-watson.properties"))]
      (let [props-to-merge (->> (utils/load-properties user-props-file)
                                (into {})
                                (into [])
                                (sort-by first))
            num-props-to-merge (count props-to-merge)]
        (println (str "Merging " num-props-to-merge " additional clj-watson "
                      (plural-props num-props-to-merge)
                      " from:\n " (.getCanonicalPath user-props-file)))
        (doseq [[k v] props-to-merge]
          ;; similar to dependency check's odc.settings.mask, but more conservative
          (println (str "  " k "=" (if (re-find #"(key|token|user|password|pw|secret)" k)
                                     "***OCCLUDED***"
                                     v)))
          (.setString settings k v)))
      (println "No additional clj-watson properties found."))
    (println)
    (set-watson-env-vars-as-properties)
    settings))

;; mock target
(defn ^:private get-nvd-api-key [settings]
  (or (.getString settings "nvd.api.key") (System/getProperty "nvd.api.key")))

(defn ^:private validate-settings
  "Validate settings, logging any findings.
  Returns {:exit <code>} when app should exit with exit <code>, else nil"
  [settings {:keys [run-without-nvd-api-key] :as opts}]
  (when (not (get-nvd-api-key settings))
    (if run-without-nvd-api-key
      (log/warn (format (str "We cannot recommend running without an nvd.api.key specified.\n"
                             "   You have opted to ignore this advice via the %s option.\n"
                             "   Expect slow NVD data updates and downloads.\n")
                        (cli-spec/styled-long-opt :run-without-nvd-api-key opts)))
      (do (log/fatal (format (str "We cannot recommend running without an nvd.api.key specified.\n"
                                  "   If you insist, rerun with the %s option, but be warned\n"
                                  "   that you will experience slow NVD data updates and downloads.")
                             (cli-spec/styled-long-opt :run-without-nvd-api-key opts)))
          {:exit 1 :exit-error "usage error"}))))

(defn ^:private build-engine [settings]
  (Engine. settings))

(defn ^:private jar-file? [dependency-path]
  (string/ends-with? dependency-path ".jar"))

(defn ^:private deps->jars [dependencies]
  (->> dependencies
       ;; as far as I understand, a dep will only ever point to a single jar
       ;; but we exclude non-jar deps and perhaps local paths
       (map :paths)
       (apply concat)
       (filter jar-file?)
       (map io/file)))

(defn ^:private scan-jars [engine jars]
  (.scan engine jars)
  (.analyzeDependencies engine)
  engine)

;; mock target
(defn ^:private scan [settings dependencies]
  (.configure (Downloader/getInstance) settings)
  (let [jars (deps->jars dependencies)
        vulnerable-jars (with-open [engine (build-engine settings)]
                          (-> engine
                              (scan-jars jars)
                              (.getDependencies)
                              (Arrays/asList)))]
    {:deps-scanned (count jars)
     :findings vulnerable-jars}))

(defn start!
  [dependencies dependency-check-properties clj-watson-properties opts]
  (let [settings (create-settings dependency-check-properties clj-watson-properties)]
    (if-let [exit (validate-settings settings opts)]
      exit
      (scan settings dependencies))))

