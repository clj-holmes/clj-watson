(ns clj-watson.controller.output
  (:require
   [cheshire.core :as json]
   [clj-watson.logic.stdout :as logic.stdout]
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]))

(defmulti ^:private generate* (fn [_ kind] (keyword kind)))

(defmethod ^:private generate* :stdout-simple [dependencies _]
  (let [template (-> "simple-report.mustache" io/resource slurp)]
    (println (logic.stdout/generate dependencies template))))

(defmethod ^:private generate* :stdout [dependencies _]
  (let [template (-> "full-report.mustache" io/resource slurp)]
    (println (logic.stdout/generate dependencies template))))

(defmethod ^:private generate* :json [dependencies _]
  (-> dependencies json/generate-string pprint/pprint))

(defmethod ^:private generate* :edn [dependencies _]
  (pprint/pprint dependencies))

(defn generate [dependencies kind]
  (generate* dependencies kind))