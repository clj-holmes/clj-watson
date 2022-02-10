(ns clj-watson.diplomat.dependency-check)

(defn update-download-database [engine]
  (binding [*out* *err*]
    (println "Downloading/Updating database.")
    (.doUpdates engine)
    (println "Download/Update completed.")))