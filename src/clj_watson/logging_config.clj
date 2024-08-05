(ns clj-watson.logging-config
  (:require
   [clojure.string :as str])
  (:import
   (ch.qos.logback.classic Level)
   (ch.qos.logback.classic.turbo TurboFilter)
   (ch.qos.logback.core.spi FilterReply)
   (org.slf4j LoggerFactory)))

(defn- create-custom-filter
  "Suppress noise from Apache Commons JCS
  - It's INFO level messages are overly noisy, only log when minimum of WARN.
  - Suppress specific ERROR level messages from IndexedDiskCache that we have deemed unimportant.

  These suppresssions could be achieved with Logback's EvaluatorFilter but that requires a
  dependency on the Janino library. We'd like to avoid adding dependencies if possible."
  []
  (proxy [TurboFilter] []
    (decide [marker logger level format params throwable]
      (cond (and (= Level/ERROR level)
                 (= "org.apache.commons.jcs3.auxiliary.disk.indexed.IndexedDiskCache"
                    (.getName logger))
                 format
                 (str/includes? format "Not alive and dispose was called"))
            FilterReply/DENY

            (and (< (.toInt level) Level/WARN_INT)
                 (str/starts-with? (.getName logger) "org.apache.commons.jcs3"))
            FilterReply/DENY

            :else
            FilterReply/NEUTRAL))))

(defn init
  "Complement `resources/logaback.xml` with some customizations"
  []
  (.addTurboFilter (LoggerFactory/getILoggerFactory) (create-custom-filter)))

(comment
  (.toInt Level/WARN)
  ;; => 30000
  (.toInt Level/INFO)
  ;; => 20000

  :eoc)
