(ns clj-watson.logic.utils
  (:require
   [clojure.java.io :as io])
  (:import
   (java.util Properties)))

(defn assoc-some
  "Associates a key with a value in a map, if and only if the value is
  not nil. From medley."
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (reduce (fn [m [k v]] (assoc-some m k v))
           (assoc-some m k v)
           (partition 2 kvs))))

(defn load-properties
  "Return `Properties` loaded from `source`."
  [source]
  (with-open [reader (io/reader source)]
    (let [props (Properties.)]
      (.load props reader)
      props)))
