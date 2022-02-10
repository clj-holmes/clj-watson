(ns clj-watson.controller.output
  (:require
   [clj-watson.logic.json :as logic.json]
   [clj-watson.logic.stdout :as logic.stdout]
   [clojure.java.io :as io]))

(defmulti ^:private generate* (fn [_ kind] (keyword kind)))

(defmethod ^:private generate* :stdout [dependencies _]
  (let [template (-> "report.mustache" io/resource slurp)]
    (logic.stdout/generate dependencies template)))

(defmethod ^:private generate* :json [dependencies _]
  (logic.json/generate dependencies))

(defmethod ^:private generate* :edn [dependencies _] dependencies)

(defn generate [dependencies kind]
  (println (generate* dependencies kind)))