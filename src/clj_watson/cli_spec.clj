(ns clj-watson.cli-spec)

(def CONFIGURATION
  {:app      {:command     "clj-watson"
              :description "run clj-holmes" :version     (System/getProperty "clj-watson.version")}
   :commands [{:command     "scan"
               :description "Performs a scan on a deps.edn file"
               :opts        [{:option  "deps-edn-path" :short "p"
                              :type    :string
                              :default :present
                              :as      "path of deps.edn to scan."}
                             {:option  "output" :short "o"
                              :type    #{"json" "edn" "stdout" "stdout-simple" "sarif"} ; keep stdout type to avoid break current automations
                              :default "stdout"
                              :as      "Output type."}
                             {:option "aliases" :short "a"
                              :type :string
                              :multiple true
                              :as "Specify a alias that will have the dependencies analysed alongside with the project deps. It's possible to provide multiple aliases. If a * is provided all the aliases are going to be analysed."}
                             {:option  "dependency-check-properties" :short "d"
                              :type    :string
                              :default nil
                              :as      "[ONLY APPLIED IF USING DEPENDENCY-CHECK STRATEGY] Path of a dependency-check properties file. If not provided uses resources/dependency-check.properties."}
                             {:option  "clj-watson-properties" :short "w"
                              :type    :string
                              :default nil
                              :as      "[ONLY APPLIED IF USING DEPENDENCY-CHECK STRATEGY] Path of an additional, optional properties file."}
                             {:option "database-strategy" :short "t"
                              :type    #{"dependency-check" "github-advisory"}
                              :default "dependency-check"
                              :as      "Vulnerability database strategy."}
                             {:option "suggest-fix" :short "s"
                              :type    :with-flag
                              :default false
                              :as "Suggest a new deps.edn file fixing all vulnerabilities found."}
                             {:option  "fail-on-result" :short "f"
                              :type    :with-flag
                              :default false
                              :as      "Enable or disable fail if results were found (useful for CI/CD)."}]
               ;; injected by clj-watson.cli to avoid circular dependency:
               :runs        nil #_entrypoint/scan}]})

(def DEFAULTS
  (into {}
        (map (fn [{:keys [option default]}]
               [(keyword option) (when-not (= default :present) default)]))
        (-> CONFIGURATION :commands first :opts)))

(def ABBREVIATIONS
  (into {}
        (map (fn [{:keys [option short]}]
               [(keyword short) (keyword option)]))
        (-> CONFIGURATION :commands first :opts)))

(defn clean-options
  "Implement defaults for tool invocation and allow for abbreviations and
   symbols as strings."
  [opts]
  (into DEFAULTS
        (comp (map (fn [[k v]] ; expand abbreviations first:
                     (if (and (contains? ABBREVIATIONS k)
                              (not (contains? opts (get ABBREVIATIONS k))))
                       [(get ABBREVIATIONS k) v]
                       [k v])))
              (map (fn [[k v]] ; supply defaults:
                     (if (contains? DEFAULTS k)
                       [k (if (some? v) v (get DEFAULTS k))]
                       [k v])))
              (map (fn [[k v]]
                     [k (if (symbol? v) (str v) v)])))
        opts))

(comment
  (clean-options {:p 'resources/deps.edn
                  :database-strategy 'dependency-check
                  :s true}))
