(ns clj-watson.controller.deps
  (:require
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.deps.alpha.util.maven :as maven])
  (:import
    (java.io File)))

(defn ^:private build-aliases [deps aliases]
  (cond
    (-> aliases set (contains? "*")) (-> deps :aliases keys)
    (coll? aliases) (map keyword aliases)
    :else []))

(defn ^:private dependencies-map->dependencies-vector [dependencies]
  (reduce (fn [dependency-vector [dependency-name dependency-info]]
            (conj dependency-vector(assoc dependency-info :dependency dependency-name)))
          [] dependencies))

(defn parse [^String deps-path aliases]
  (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge maven/standard-repos))
        aliases (build-aliases project-deps aliases)
        aliases-resolver {:resolve-args (deps/combine-aliases project-deps aliases)
                          :classpath-args (deps/combine-aliases project-deps aliases)}]
    {:deps project-deps
     :dependencies (-> project-deps (deps/calc-basis aliases-resolver) :libs dependencies-map->dependencies-vector)}))

(comment
  (parse "resources/vulnerable-deps.edn" nil))