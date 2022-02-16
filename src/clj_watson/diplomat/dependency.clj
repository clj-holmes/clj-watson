(ns clj-watson.diplomat.dependency
  (:require
    [clojure.tools.deps.alpha.extensions :as ext]
    [clojure.tools.deps.alpha.util.maven :as maven]
    [clojure.tools.deps.alpha :as deps]))

(defn ^:private get-all-versions!*
  ([dependency]
   (get-all-versions!* dependency maven/standard-repos))
  ([dependency repositories]
   (binding [*out* *err*]
     (when dependency
       (let [versions (ext/find-all-versions dependency nil repositories)]
         (map :mvn/version versions))))))

(def get-all-versions! (memoize get-all-versions!*))

(defn resolve-dependency! [deps]
  (try
    (deps/calc-basis deps {})
    (catch Exception e
      (binding [*out* *err*]
        (println (ex-message e))))))

(comment
  (def dependency 'com.datomic/dev-local)

  (def project-deps {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                 "clojars" {:url "https://repo.clojars.org/"}}})

  (get-all-versions!* 'com.auth0/java-jwt project-deps)
  (get-all-versions! dependency project-deps))