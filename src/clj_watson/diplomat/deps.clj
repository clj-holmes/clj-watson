(ns clj-watson.diplomat.deps
  (:require
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.util.maven :as maven])
  (:import
   (java.io File)))

(defn build-aliases [deps aliases]
  (cond
    (-> aliases set (contains? "*")) (-> deps :aliases keys)
    (coll? aliases) (map keyword aliases)
    :else []))

(defn read-and-resolve [^String deps-path aliases]
  (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge maven/standard-repos))
        aliases (build-aliases project-deps aliases)
        aliases-resolver {:resolve-args (deps/combine-aliases project-deps aliases)
                          :classpath-args (deps/combine-aliases project-deps aliases)}]
    {:project-deps project-deps
     :dependencies (-> project-deps (deps/calc-basis aliases-resolver) :libs)}))

(comment
  (read-and-resolve "resources/vulnerable-deps.edn" nil))