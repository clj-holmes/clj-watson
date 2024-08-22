(ns clj-watson.logic.sarif
  (:require
   [clj-watson.logic.template :as logic.template]
   [clojure.java.io :as io]
   [clojure.string :as string]))

(def ^:private sarif-boilerplate
  {:$schema "https://schemastore.azurewebsites.net/schemas/json/sarif-2.1.0-rtm.5.json"
   :version "2.1.0"
   :runs    [{:tool
              {:driver {:name           "clj-watson"
                        :informationUri "https://github.com/clj-holmes/clj-watson"
                        :version        "3.0.2"}}}]})

(defn ^:private advisory->sarif-rule [dependency dependency-info {{:keys [description summary identifiers cvss]} :advisory}]
  (let [identifier (-> identifiers first :value)
        ; needs to remove it from here
        template (-> "sarif-help.mustache" io/resource slurp)
        help-text (logic.template/generate {:vulnerable-dependency dependency-info :identifiers identifiers} template)]
    [{:id                   identifier
      :name                 (format "VulnerableDependency%s" (-> dependency name string/capitalize))
      :shortDescription     {:text summary}
      :fullDescription      {:text description}
      :help                 {:text help-text
                             :markdown help-text}
      :helpUri              (format "https://github.com/advisories/%s" identifier)
      :properties           {:security-severity (some-> cvss :score str)
                             :cvss cvss}
      :defaultConfiguration {:level "error"}}]))

(defn ^:private dependencies->sarif-rules [dependencies]
  (->> dependencies
       (map (fn [{:keys [dependency vulnerabilities] :as dependency-info}]
              (->> vulnerabilities
                   (map #(advisory->sarif-rule dependency dependency-info %))
                   (reduce concat))))
       (reduce concat)))

(defn ^:private advisory->sarif-result
  [filename physical-location dependency {{:keys [identifiers]} :advisory}]
  {:ruleId    (-> identifiers first :value)
   :message   {:text (format "Vulnerability found in direct dependency %s" dependency)}
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
