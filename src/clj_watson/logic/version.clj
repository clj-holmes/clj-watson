(ns clj-watson.logic.version
  "Use Maven logic as the authority for comparing versions."
  (:import
   [org.apache.maven.artifact.versioning ComparableVersion]))

(set! *warn-on-reflection* true)

(defn version-compare
  "Compares version strings, returns:
  - -1 if x < y 
  -  0 if x = y 
  -  1 if x > y"
  [x y]
  (.compareTo (ComparableVersion. x) (ComparableVersion. y)))

(defn newer?
  "Returns true if x > y"
  [x y]
  (pos? (version-compare x y)))

(defn older?
  "Returns true if x < y"
  [x y]
  (neg? (version-compare x y)))

(defn newer-or-equal?
  "Returns true if x >= y"
  [x y]
  (not (older? x y)))

(defn older-or-equal?
  "Returns true if x <= y"
  [x y]
  (not (newer? x y)))

