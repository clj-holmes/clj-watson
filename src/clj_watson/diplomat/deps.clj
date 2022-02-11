(ns clj-watson.diplomat.deps
  (:require
   [clojure.tools.deps.alpha :as deps])
  (:import
   (java.io File)))

(def ^:private default-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(defn build-aliases [deps aliases]
  (cond
    (-> aliases set (contains? "*")) (-> deps :aliases keys)
    (coll? aliases) (map keyword aliases)
    :else []))

(defn read-and-resolve [^String deps-path aliases]
  (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge default-repositories))
        aliases (build-aliases project-deps aliases)
        aliases-resolver {:resolve-args (deps/combine-aliases project-deps aliases)
                          :classpath-args (deps/combine-aliases project-deps aliases)}]
    {:project-deps project-deps
     :dependencies (-> project-deps (deps/calc-basis aliases-resolver) :libs)}))

(comment
  (read-and-resolve "deps.edn" nil))