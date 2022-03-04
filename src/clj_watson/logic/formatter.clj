(ns clj-watson.logic.formatter)

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

(defn dependencies-hierarchy-to-tree [trees]
  (if (and (= 1 (count trees)) (every? empty? trees))
    "Direct dependency."
    (->> trees
         reverse
         (map dependencies-hierarchy-to-tree*)
         (reduce #(str %1 "\n" %2)))))