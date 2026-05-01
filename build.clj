(load-file "src/clj_watson/version.clj")

(ns build
  (:require
   [clj-watson.version :as version]
   [clojure.tools.build.api :as b]
   [clojure.java.io :as io]
   [clojure.string :as string]))

(defn sanity [_]
  (let [version (version/get-version)]
    (println "Version is:" version)))
