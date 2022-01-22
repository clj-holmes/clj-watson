(ns clj-watson.diplomat.deps
  (:require [clojure.tools.deps.alpha :as deps])
  (:import (java.io File)))

(def ^:private default-repositories
  {"central" {:url "https://repo1.maven.org/maven2/"}
   "clojars" {:url "https://repo.clojars.org/"}})

(defn read-and-resolve [^String deps-path]
  (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge default-repositories ))]
    {:project-deps project-deps
     :dependencies (-> project-deps (deps/resolve-deps {}))}))

(comment
  (read-and-resolve "deps.edn"))