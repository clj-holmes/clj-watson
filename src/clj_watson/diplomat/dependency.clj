(ns clj-watson.diplomat.dependency
  (:require
    [clojure.tools.deps.alpha.extensions :as ext]))

(defn get-all-versions [dependency project-deps]
  (binding [*out* *err*]
    (let [versions (some-> dependency
                           (ext/find-all-versions nil project-deps))]
      (map :mvn/version versions))))

(comment
  (def dependency 'org.clojure/clojure)
  (def project-deps {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                 "clojars" {:url "https://repo.clojars.org/"}}})
  (get-all-versions dependency project-deps))