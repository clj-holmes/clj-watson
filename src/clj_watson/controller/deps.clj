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

(defn parse [^String classpath ^String deps-path aliases]
  (if classpath
    {:deps {:mvn/repos maven/standard-repos}
     :dependencies (mapv (fn [path] {:paths [path]})
                         (.split classpath (System/getProperty "path.separator")))}
    (let [project-deps (-> deps-path File. deps/slurp-deps (update :mvn/repos merge maven/standard-repos))
          aliases (build-aliases project-deps aliases)
          dependencies-physical-location (deps->dependencies-location deps-path)
          aliases-resolver {:resolve-args (deps/combine-aliases project-deps aliases)
                            :classpath-args (deps/combine-aliases project-deps aliases)}]
      {:deps project-deps
       :dependencies (-> project-deps
                         (deps/calc-basis aliases-resolver)
                         :libs
                         (dependencies-map->dependencies-vector dependencies-physical-location))})))

(comment
  (parse nil "resources/vulnerable-deps.edn" nil)
  ;; -Spath output from my machine for testing:
  (parse "src:/home/sean/.m2/repository/com/auth0/java-jwt/3.5.0/java-jwt-3.5.0.jar:/home/sean/.m2/repository/image-resizer/image-resizer/0.1.10/image-resizer-0.1.10.jar:/home/sean/.m2/repository/io/replikativ/datahike/0.4.1480/datahike-0.4.1480.jar:/home/sean/.m2/repository/org/clojure/clojure/1.11.0-alpha1/clojure-1.11.0-alpha1.jar:/home/sean/.m2/repository/org/postgresql/postgresql/42.2.10/postgresql-42.2.10.jar:/home/sean/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.9.8/jackson-databind-2.9.8.jar:/home/sean/.m2/repository/org/imgscalr/imgscalr-lib/4.2/imgscalr-lib-4.2.jar:/home/sean/.m2/repository/com/taoensso/timbre/5.1.2/timbre-5.1.2.jar:/home/sean/.m2/repository/environ/environ/1.2.0/environ-1.2.0.jar:/home/sean/.m2/repository/io/lambdaforge/datalog-parser/0.1.9/datalog-parser-0.1.9.jar:/home/sean/.m2/repository/io/replikativ/hitchhiker-tree/0.1.11/hitchhiker-tree-0.1.11.jar:/home/sean/.m2/repository/io/replikativ/superv.async/0.2.11/superv.async-0.2.11.jar:/home/sean/.m2/repository/io/replikativ/zufall/0.1.0/zufall-0.1.0.jar:/home/sean/.m2/repository/junit/junit/4.13.2/junit-4.13.2.jar:/home/sean/.m2/repository/org/clojure/clojurescript/1.11.4/clojurescript-1.11.4.jar:/home/sean/.m2/repository/org/clojure/tools.reader/1.3.6/tools.reader-1.3.6.jar:/home/sean/.m2/repository/persistent-sorted-set/persistent-sorted-set/0.1.4/persistent-sorted-set-0.1.4.jar:/home/sean/.m2/repository/org/clojure/core.specs.alpha/0.2.56/core.specs.alpha-0.2.56.jar:/home/sean/.m2/repository/org/clojure/spec.alpha/0.2.194/spec.alpha-0.2.194.jar:/home/sean/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar:/home/sean/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar:/home/sean/.m2/repository/com/taoensso/encore/3.12.1/encore-3.12.1.jar:/home/sean/.m2/repository/io/aviso/pretty/0.1.37/pretty-0.1.37.jar:/home/sean/.m2/repository/com/taoensso/carmine/2.20.0/carmine-2.20.0.jar:/home/sean/.m2/repository/io/replikativ/konserve/0.5.1/konserve-0.5.1.jar:/home/sean/.m2/repository/org/clojure/core.cache/1.0.207/core.cache-1.0.207.jar:/home/sean/.m2/repository/org/clojure/core.memoize/1.0.236/core.memoize-1.0.236.jar:/home/sean/.m2/repository/org/clojure/core.rrb-vector/0.1.1/core.rrb-vector-0.1.1.jar:/home/sean/.m2/repository/org/clojure/core.async/1.3.610/core.async-1.3.610.jar:/home/sean/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:/home/sean/.m2/repository/com/cognitect/transit-clj/0.8.309/transit-clj-0.8.309.jar:/home/sean/.m2/repository/com/google/javascript/closure-compiler-unshaded/v20210808/closure-compiler-unshaded-v20210808.jar:/home/sean/.m2/repository/org/clojure/data.json/0.2.6/data.json-0.2.6.jar:/home/sean/.m2/repository/org/clojure/google-closure-library/0.0-20211011-0726fdeb/google-closure-library-0.0-20211011-0726fdeb.jar:/home/sean/.m2/repository/com/taoensso/truss/1.6.0/truss-1.6.0.jar:/home/sean/.m2/repository/com/taoensso/nippy/2.14.0/nippy-2.14.0.jar:/home/sean/.m2/repository/commons-codec/commons-codec/1.13/commons-codec-1.13.jar:/home/sean/.m2/repository/org/apache/commons/commons-pool2/2.4.2/commons-pool2-2.4.2.jar:/home/sean/.m2/repository/fress/fress/0.3.1/fress-0.3.1.jar:/home/sean/.m2/repository/io/replikativ/hasch/0.3.5/hasch-0.3.5.jar:/home/sean/.m2/repository/io/replikativ/incognito/0.2.5/incognito-0.2.5.jar:/home/sean/.m2/repository/org/clojars/mmb90/cljs-cache/0.1.4/cljs-cache-0.1.4.jar:/home/sean/.m2/repository/org/clojure/data.fressian/0.2.1/data.fressian-0.2.1.jar:/home/sean/.m2/repository/org/clojure/data.priority-map/1.0.0/data.priority-map-1.0.0.jar:/home/sean/.m2/repository/org/clojure/tools.analyzer.jvm/1.1.0/tools.analyzer.jvm-1.1.0.jar:/home/sean/.m2/repository/com/cognitect/transit-java/0.8.332/transit-java-0.8.332.jar:/home/sean/.m2/repository/org/clojure/google-closure-library-third-party/0.0-20211011-0726fdeb/google-closure-library-third-party-0.0-20211011-0726fdeb.jar:/home/sean/.m2/repository/net/jpountz/lz4/lz4/1.3/lz4-1.3.jar:/home/sean/.m2/repository/org/iq80/snappy/snappy/0.4/snappy-0.4.jar:/home/sean/.m2/repository/org/tukaani/xz/1.6/xz-1.6.jar:/home/sean/.m2/repository/org/clojure/data.codec/0.1.1/data.codec-0.1.1.jar:/home/sean/.m2/repository/adzerk/boot-test/1.1.2/boot-test-1.1.2.jar:/home/sean/.m2/repository/crisptrutski/boot-cljs-test/0.2.2-SNAPSHOT/boot-cljs-test-0.2.2-SNAPSHOT.jar:/home/sean/.m2/repository/tailrecursion/cljs-priority-map/1.2.1/cljs-priority-map-1.2.1.jar:/home/sean/.m2/repository/org/fressian/fressian/0.6.6/fressian-0.6.6.jar:/home/sean/.m2/repository/org/clojure/tools.analyzer/1.0.0/tools.analyzer-1.0.0.jar:/home/sean/.m2/repository/org/ow2/asm/asm/5.2/asm-5.2.jar:/home/sean/.m2/repository/org/msgpack/msgpack/0.6.12/msgpack-0.6.12.jar:/home/sean/.m2/repository/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar:/home/sean/.m2/repository/org/javassist/javassist/3.18.1-GA/javassist-3.18.1-GA.jar" nil nil))
