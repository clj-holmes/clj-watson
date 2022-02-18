(ns clj-watson.logic.stdout
  (:require
   [selmer.filters :refer [add-filter!]]
   [selmer.parser :refer [render]]))

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

(defn ^:private dependencies-hierarchy-to-tree [trees]
  (if (and (= 1 (count trees)) (every? empty? trees))
    "Direct dependency."
    (->> trees
         reverse
         (map dependencies-hierarchy-to-tree*)
         (reduce #(str %1 "\n" %2)))))

(add-filter! :build-tree dependencies-hierarchy-to-tree)

(defn generate [dependencies template]
  (render template {:vulnerable-dependencies dependencies}))