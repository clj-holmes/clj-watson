(ns clj-watson.version
  "clj-watson's own version (read from clj-watson-version.txt as needed)."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn get-version []
  (-> "clj-watson-version.txt" io/resource slurp str/trim))
