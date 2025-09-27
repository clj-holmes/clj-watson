(ns clj-watson.test-util
  (:require
   [clojure.string :as str]
   [clojure.tools.logging :as log]))

(defmacro with-out-capture
  [& body]
  `(let [out# (new java.io.StringWriter)
         err# (new java.io.StringWriter)]
     (binding [*out* out#
               *err* err#]
       (let [res# (try ~@body
                       (catch Throwable ex#
                         {:ex (Throwable->map ex#)}))]
         {:result res#
          :out-lines (str/split-lines (str out#))
          :err-lines (str/split-lines (str err#))}))))

(defmacro with-log-capture
  "Capturing log events from stdout/stderr is not reliable, so support capturing events as the source."
  [[binding-name _opts] & body]
  `(let [~binding-name (atom [])]
     (with-redefs [log/log* (fn [_logger# level# _throwable# message#]
                              (swap! ~binding-name conj [level# (str/split-lines message#)])
                              nil)]
       ~@body)))
