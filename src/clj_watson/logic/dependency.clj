(ns clj-watson.logic.dependency)

(defn get-dependency-version [dependency]
  (or (:mvn/version dependency)
      (:git/tag dependency)))