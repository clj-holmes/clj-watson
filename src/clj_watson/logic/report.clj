(ns clj-watson.logic.report
  (:require
   [clojure.edn :as edn]))

(defn ^:private dependencies-hierarchy-to-tree* [tree]
  (loop [text ""
         count 0
         dependencies tree]
    (if (nil? dependencies)
      text
      (let [tabs (apply str (repeat count "\t"))
            dependency (first dependencies)
            new-text (format "%s%s[%s]\n" text tabs dependency)]
        (recur new-text (inc count) (next dependencies))))))

(defn dependencies-hierarchy-to-tree [text]
  (fn [render-fn]
    (when-let [trees (some-> text render-fn edn/read-string)]
      (->> trees
           reverse
           (map dependencies-hierarchy-to-tree*)
           (reduce #(str %1 "\n" %2))))))