(ns clj-watson.diplomat.dependency
  (:require
   [clojure.tools.deps :as deps]
   [clojure.tools.deps.extensions :as ext]
   [clojure.tools.deps.extensions.git :as git]
   [clojure.tools.deps.util.maven :as maven]
   [clojure.tools.gitlibs :as gitlibs]))

(defn ^:private append-sha-when-is-git-version [dependency version]
  (if (:git/tag version)
    (let [git-url (git/auto-git-url dependency)
          sha (gitlibs/resolve git-url (:git/tag version))]
      (assoc version :git/sha sha))
    version))

(defn ^:private get-all-versions!*
  ([dependency]
   (get-all-versions!* dependency maven/standard-repos))
  ([dependency repositories]
   (binding [*out* *err*]
     (when dependency
       (->> (ext/find-all-versions dependency nil repositories)
            (map #(append-sha-when-is-git-version dependency %)))))))

(def get-all-versions! (memoize get-all-versions!*))

(defn ^:private get-latest-version!*
  ([dependency]
   (get-latest-version!* dependency maven/standard-repos))
  ([dependency repositories]
   (binding [*out* *err*]
     (when dependency
       (->> (ext/find-all-versions dependency nil repositories)
            last
            (append-sha-when-is-git-version dependency))))))

(def get-latest-version! (memoize get-latest-version!*))

(defn resolve-dependency! [deps]
  (try
    (deps/calc-basis deps {})
    (catch Exception e
      (binding [*out* *err*]
        (println (ex-message e))))))

(comment
  (get-latest-version! 'org.clojure/clojure {:mvn/repos maven/standard-repos})
  (get-latest-version! 'io.github.clj-holmes/clj-watson {:mvn/repos maven/standard-repos})
  (resolve-dependency! {:deps      {'io.github.clj-holmes/clj-watson {:git/tag "v5.1.1" :git/sha "ad5fe07"}}
                        :mvn/repos maven/standard-repos}))
