(ns clj-watson.logic.utils
  (:require [clojure.string :as string]))

(defn clojure-file? [dependency-path]
  (string/ends-with? dependency-path ".jar"))