(ns clj-watson.logic.json
  (:require
   [clojure.data.json :as json]))

(defn generate [dependencies]
  (json/write-str dependencies))