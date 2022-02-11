(ns clj-watson.diplomat.deps
  (:require
   [clojure.tools.deps.alpha :as deps])
  (:import
   (java.io File)))

(def ^:private default-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(defn read-and-resolve [^String deps-path]
  (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge default-repositories))
        aliases (-> project-deps :aliases keys)
        aliases-resolver {:resolve-args (deps/combine-aliases project-deps aliases)
                          :classpath-args (deps/combine-aliases project-deps aliases)}]
    {:project-deps project-deps
     :dependencies (-> project-deps (deps/calc-basis aliases-resolver) :libs)}))

(comment
  (deps/resolve-deps {:deps         {'org.clojure/clojure {:mvn/version "1.9.0"}}
                      :repositories default-repositories} {}))