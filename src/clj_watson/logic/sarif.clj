(ns clj-watson.logic.sarif
  (:require
   [clojure.string :as string]))

(def ^:private sarif-boilerplate
  {:$schema "https://www.schemastore.org/schemas/json/sarif-2.1.0-rtm.5.json"
   :version "2.1.0"
   :runs    [{:tool
              {:driver {:name           "clj-watson"
                        :informationUri "https://github.com/clj-holmes/clj-watson"
                        :version        "3.0.1"}}}]})

(defn ^:private advisory->sarif-rule [dependency {{:keys [description summary identifiers cvss]} :advisory}]
  (let [identifier (-> identifiers first :value)]
    [{:id                   identifier
      :name                 (format "VulnerableDependency%s" (-> dependency name string/capitalize))
      :shortDescription     {:text summary}
      :fullDescription      {:text description}
      :help                 {:text (format "Vulnerability found in package %s" dependency)}
      :helpUri              (format "https://github.com/advisories/%s" identifier)
      :properties           {:security-severity (:score cvss)}
      :defaultConfiguration {:level "error"}}]))

(defn ^:private dependencies->sarif-rules [dependencies]
  (->> dependencies
       (map (fn [{:keys [dependency vulnerabilities]}]
              (->> vulnerabilities
                   (map #(advisory->sarif-rule dependency %))
                   (reduce concat))))
       (reduce concat)))

(defn ^:private advisory->sarif-result [filename physical-location dependency {{:keys [identifiers]} :advisory}]
  {:ruleId    (-> identifiers first :value)
   :message   {:text (format "Vulnerability found in package %s" dependency)}
   :locations [{:physicalLocation
                {:artifactLocation {:uri filename}
                 :region           physical-location}}]})

(defn ^:private dependencies->sarif-results [dependencies deps-edn-path]
  (->> dependencies
       (map (fn [{:keys [dependency vulnerabilities physical-location]}]
              (->> vulnerabilities
                   (map #(advisory->sarif-result deps-edn-path physical-location dependency %)))))
       (reduce concat)))

(defn generate [dependencies deps-edn-path]
  (let [rules (dependencies->sarif-rules dependencies)
        results (dependencies->sarif-results dependencies deps-edn-path)]
    (-> sarif-boilerplate
        (assoc-in [:runs 0 :tool :driver :rules] rules)
        (assoc-in [:runs 0 :results] results))))