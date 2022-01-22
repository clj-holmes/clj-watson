(ns clj-watson.controller.output.report
  (:require [cljstache.core :refer [render-resource]]
            [clj-watson.logic.report :as logic.report]))

(def ^:private template-path "report.mustache")

(defn generate [dependencies]
  (->
    template-path
    (render-resource
      {:dependencies dependencies
       :build-tree   logic.report/dependencies-hierarchy-to-tree})
    println))