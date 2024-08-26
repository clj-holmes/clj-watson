(ns clj-watson.controller.output
  (:require
   [cheshire.core :as json]
   [clj-watson.logic.sarif :as logic.sarif]
   [clj-watson.logic.template :as logic.template]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]))

(defmulti ^:private generate* (fn [_ _ kind] kind))

(defmethod ^:private generate* :stdout-simple [dependencies & _]
  (let [template (-> "simple-report.mustache" io/resource slurp)]
    (println (logic.template/generate {:vulnerable-dependencies dependencies} template))))

(defmethod ^:private generate* :stdout [dependencies & _]
  (let [template (-> "full-report.mustache" io/resource slurp)]
    (println (logic.template/generate {:vulnerable-dependencies dependencies} template))))

(defmethod ^:private generate* :json [dependencies & _]
  (-> dependencies (json/generate-string {:pretty true}) println))

(defmethod ^:private generate* :edn [dependencies & _]
  (pprint/pprint dependencies))

(defmethod ^:private generate* :sarif [dependencies deps-edn-path & _]
  (-> dependencies (logic.sarif/generate deps-edn-path) (json/generate-string {:pretty true}) println))

(defn generate [dependencies deps-edn-path kind]
  (generate* dependencies deps-edn-path kind))

(defn final-summary
  "See `clj-waston.logic.summarize/summary` for description"
  [{:keys [cnt-deps-scanned cnt-deps-vulnerable
           cnt-deps-severities cnt-deps-unexpected-severities cnt-deps-unspecified-severity]}]
  (let [details (->> [(when (seq cnt-deps-severities)
                        (format "(%s)"
                                (->> cnt-deps-severities
                                     (mapv (fn [[severity count]] (format "%d %s" count severity)))
                                     (str/join ", "))))
                      (when (seq cnt-deps-unexpected-severities)
                        (format "Unrecognized severities: (%s)"
                                (->> cnt-deps-unexpected-severities
                                     (mapv (fn [[severity count]] (format "%d %s" count severity)))
                                     (str/join ", "))))
                      (when (and cnt-deps-unspecified-severity (> cnt-deps-unspecified-severity 0))
                        (format "Unspecified severities: %d" cnt-deps-unspecified-severity))]
                     (keep identity))]
    ;; Dependencies scanned: 151
    ;; Vulnerable dependencies found: 9 (4 Critical, 1 High, 2 Medium), Unrecognize d severities: (2 Foobar), Unspecified severities: 3
    (println (format "Dependencies scanned: %d" cnt-deps-scanned))
    (println (format "Vulnerable dependencies found: %d%s"
                     cnt-deps-vulnerable
                     (if (seq details)
                       (str " " (str/join ", " details))
                       "")))))
