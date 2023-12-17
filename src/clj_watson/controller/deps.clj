(ns clj-watson.controller.deps
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.util.maven :as maven]
   [edamame.core :refer [parse-string]])
  (:import
   (java.io File)))

(defn ^:private build-aliases [deps aliases]
  (cond
    (-> aliases set (contains? "*")) (-> deps :aliases keys)
    (coll? aliases) (map keyword aliases)
    :else []))

(defn ^:private dependencies-map->dependencies-vector [dependencies dependencies-physical-location]
  (reduce (fn [dependency-vector [dependency-name dependency-info]]
            (let [dependency-physical-location (or (get dependencies-physical-location dependency-name)
                                                   (->> dependency-info
                                                        :parents
                                                        ffirst
                                                        (get dependencies-physical-location)))]
              (->> (assoc dependency-info :dependency dependency-name :physical-location dependency-physical-location)
                   (conj dependency-vector))))
          [] dependencies))

(defn ^:private rename-location-keys [location]
  (rename-keys location {:row :startLine :end-row :endLine :col :startColumn :end-col :endColumn}))

(defn ^:private deps->dependencies-location [deps-path]
  (let [deps (-> deps-path slurp parse-string)]
    (->> (tree-seq coll? identity deps)
         (filter symbol?)
         (reduce (fn [locations element]
                   (if-let [location (some-> element meta rename-location-keys)]
                     (assoc locations element location)
                     locations))
                 {}))))

(defn parse [^String deps-path aliases]
  (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge maven/standard-repos))
        aliases (build-aliases project-deps aliases)
        dependencies-physical-location (deps->dependencies-location deps-path)
        aliases-resolver {:resolve-args (deps/combine-aliases project-deps aliases)
                          :classpath-args (deps/combine-aliases project-deps aliases)}]
    {:deps project-deps
     :dependencies (-> project-deps
                       (deps/calc-basis aliases-resolver)
                       :libs
                       (dependencies-map->dependencies-vector dependencies-physical-location))}))

(comment
  (parse "resources/vulnerable-deps.edn" nil))
