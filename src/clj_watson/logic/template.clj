(ns clj-watson.logic.template
  (:require
   [clj-watson.logic.formatter :refer [dependencies-hierarchy-to-tree]]
   [selmer.filters :refer [add-filter!]]
   [selmer.parser :refer [render]]))

(add-filter! :build-tree dependencies-hierarchy-to-tree)

(defn generate [dependencies template]
  (render template dependencies))