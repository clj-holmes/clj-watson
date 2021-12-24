(ns clj-watson.vulnerabilities)

(defn ^:private cvssv2 [vulnerability]
  (try
    (some-> vulnerability .getCvssV2 .getScore)
    (catch Exception _
      nil)))

(defn ^:private cvssv3 [vulnerability]
  (try
    (some-> vulnerability .getCvssV3 .getBaseScore)
    (catch Exception _
      nil)))

(defn ^:private cwes [vulnerability]
  (some->> vulnerability
           .getCwes
           .getFullCwes
           keys
           set))

(defn ^:private cpe-version [vulnerability]
  (->> vulnerability
       .getVulnerableSoftware
       (map (fn [software]
              (let [version (.getVersion software)]
                (when-not (contains? #{"*" "-"} version)
                  version))))
       (filterv identity)))

(defn ^:private version-end-excluding [vulnerability]
  (->> vulnerability
       .getVulnerableSoftware
       (map #(.getVersionEndExcluding %))
       (filterv identity)))

(defn get-details
  [vulnerability]
  {:name                  (.getName vulnerability)
   :cpe-version           (cpe-version vulnerability)
   :version-end-excluding (version-end-excluding vulnerability)
   :cvssv2                (cvssv2 vulnerability)
   :cvssv3                (cvssv3 vulnerability)
   :cwes                  (cwes vulnerability)})