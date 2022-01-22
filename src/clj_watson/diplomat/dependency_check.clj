(ns clj-watson.diplomat.dependency-check)

(defn update-download-database [engine]
  (println "Downloading/Updating database.")
  (.doUpdates engine)
  (println "Download/Update completed."))