(ns clj-watson.diplomat.dependency
  (:require
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.maven :as maven]))

(defn get-all-versions
  ([dependency]
   (get-all-versions dependency maven/standard-repos))
  ([dependency repositories]
   (binding [*out* *err*]
     (when dependency
       (let [versions (ext/find-all-versions dependency nil repositories)]
         (map :mvn/version versions))))))

(comment
  (def dependency 'com.datomic/dev-local)

  (def project-deps {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                 "clojars" {:url "https://repo.clojars.org/"}}})

  (get-all-versions dependency project-deps))