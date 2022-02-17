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
         (pmap :mvn/version versions))))))

(def get-all-versions! (memoize get-all-versions!*))

(defn resolve-dependency! [deps]
  (try
    (deps/calc-basis deps {})
    (catch Exception e
      (binding [*out* *err*]
        (println (ex-message e))))))

(comment
  (require '[clojure.tools.deps.alpha.util.maven :as maven])

  (def dependency 'com.datomic/dev-local)

  (get-all-versions!* 'com.google.javascript/closure-compiler-unshaded {:mvn/repos maven/standard-repos})

  (resolve-dependency! {:deps      {'com.google.javascript/closure-compiler-unshaded {:mvn/version "v20220202"}}
                        :mvn/repos maven/standard-repos})

  (ext/find-all-versions 'com.google.javascript/closure-compiler-unshaded nil {:mvn/repos maven/standard-repos})
  )