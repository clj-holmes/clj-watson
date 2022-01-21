(ns clj-watson.report
  (:require
   [cljstache.core :refer [render]]
   [clojure.edn :as edn]))

(defn ^:private dependencies-tree* [tree]
  (loop [text ""
         count 0
         dependencies tree]
    (if (nil? dependencies)
      text
      (let [tabs (apply str (repeat count "\t"))
            dependency (first dependencies)
            new-text (format "%s%s[%s]\n" text tabs dependency)]
        (recur new-text (inc count) (next dependencies))))))

(defn ^:private dependencies-tree [text]
  (fn [render-fn]
    (let [trees (-> text render-fn edn/read-string)]
      (->> trees
           reverse
           (map dependencies-tree*)
           (reduce #(str %1 "\n" %2))))))

(defn generate [dependencies]
  (let [report-template (slurp "resources/report.mustache")]
    (render report-template {:dependencies dependencies
                             :build-tree   dependencies-tree})))