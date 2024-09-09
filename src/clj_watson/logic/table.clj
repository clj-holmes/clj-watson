(ns clj-watson.logic.table
  "Most of this table support is stolen/adapted from babashka.cli"
  (:require
   [clojure.string :as str]))

(defn- str-width
  "Width of `s` when printed, i.e. without ANSI escape codes."
  [s]
  (let [strip-escape-codes #(str/replace %
                                         (re-pattern "(\\x9B|\\x1B\\[)[0-?]*[ -\\/]*[@-~]") "")]
    (count (strip-escape-codes s))))

(defn- pad [len s] (str s (apply str (repeat (- len (str-width s)) " "))))

(defn- pad-cells
  "Adapted from bb cli"
  [rows widths]
  (let [pad-row (fn [row]
                  (map (fn [width cell] (pad width cell)) widths row))]
    (map pad-row rows)))

(defn- expand-multilines
  "Expand last column cell over multiple rows if it contains newlines"
  [rows]
  (reduce (fn [acc row]
            (let [[line & extra-lines] (-> row last str/split-lines)
                  cols (count row)]
              (if (seq extra-lines)
                (apply conj acc
                       (assoc (into [] row) (dec cols) line)
                       (map #(conj (into [] (repeat (dec cols) ""))
                                   %)
                            extra-lines))
                (conj acc row))))
          []
          rows))

(defn cell-widths [rows]
  (reduce
   (fn [widths row]
     (map max (map str-width row) widths)) (repeat 0) rows))

(defn format-table
  "Modified from bb cli format-table. Allow pre-computed widths to be passed in."
  [{:keys [rows indent widths] :or {indent 2}}]
  (let [widths (or widths (cell-widths rows))
        rows (expand-multilines rows)
        rows (pad-cells rows widths)
        fmt-row (fn [leader divider trailer row]
                  (str leader
                       (apply str (interpose divider row))
                       trailer))]
    (->> rows
         (map (fn [row]
                (fmt-row (apply str (repeat indent " ")) " " "" row)))
         (map str/trimr)
         (str/join "\n"))))

(comment
  (-> (format-table {:rows [["r1c1" "r1c2" "r1c3"]
                            ["r2c1 wider" "r2c2" "r2c3"]
                            ["r3c1" "r3c2 wider" "r3c3"]]})
      str/split-lines)
  ;; => ["  r1c1       r1c2       r1c3"
  ;;     "  r2c1 wider r2c2       r2c3"
  ;;     "  r3c1       r3c2 wider r3c3"]

  :eoc)
