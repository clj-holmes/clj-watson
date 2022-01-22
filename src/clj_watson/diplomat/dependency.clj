(ns clj-watson.diplomat.dependency
  (:require
   [clojure.tools.deps.alpha.extensions :as ext]))

(defn get-latest-version [dependency project-deps]
  (some-> dependency
          (ext/find-all-versions nil project-deps)
          last
          :mvn/version))