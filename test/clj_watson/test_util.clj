(ns clj-watson.test-util
  (:require
   [clojure.string :as str]
   [clojure.test]
   [clojure.tools.logging :as log]))

(def platform
  (str "clj " (clojure-version) " jdk " (System/getProperty "java.version")))

(defmethod clojure.test/report :begin-test-var [m]
  (let [test-name (-> m :var meta :name)]
    (println (format "=== %s [%s]" test-name platform))))

(defn pool-debug-fixture [f]
  (println "-----executors before---->")
  (println " pooled " clojure.lang.Agent/pooledExecutor)
  (println " solo" clojure.lang.Agent/soloExecutor)
  (f)
  (println "-----executors after---->")
  (println " pooled " clojure.lang.Agent/pooledExecutor)
  (println " solo" clojure.lang.Agent/soloExecutor))

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
