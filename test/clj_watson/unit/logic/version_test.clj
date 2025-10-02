(ns clj-watson.unit.logic.version-test
  (:require
   [clj-watson.logic.version :as version]
   [clojure.test :refer [deftest is]]
   [matcher-combinators.test]))

(deftest compares-version-strings-oldest-to-newest-test
  (is (match? ["1.0.0"
               "2-beta28"
               "2-beta29"
               "2-beta30"
               "2-beta31.1"
               "2-beta32"
               "2-beta33"
               "2-beta34"
               "2-beta35"
               "2-beta36"
               "2-beta37"
               "2-beta38"
               "2-beta39"
               "2-beta40"
               "2-beta49"
               "2-beta50"
               "2-beta51"
               "2-beta52"
               "2-beta53"
               "2-beta54-alpha1"
               "2-beta54-alpha2"
               "2-beta54-alpha2.1"
               "2-beta54-alpha3"
               "2-beta54-rc1"
               "2-beta54-SNAPSHOT"
               "2-beta54"
               "2.0.0"]
              (->> ["1.0.0"
                    "2.0.0"
                    "2-beta31.1"
                    "2-beta54"
                    "2-beta54-SNAPSHOT"
                    "2-beta54-alpha1"
                    "2-beta54-alpha2.1"
                    "2-beta54-alpha2"
                    "2-beta54-alpha3"
                    "2-beta54-rc1"
                    "2-beta53"
                    "2-beta52"
                    "2-beta51"
                    "2-beta50"
                    "2-beta49"
                    "2-beta40"
                    "2-beta39"
                    "2-beta38"
                    "2-beta37"
                    "2-beta36"
                    "2-beta35"
                    "2-beta34"
                    "2-beta33"
                    "2-beta32"
                    "2-beta30"
                    "2-beta29"
                    "2-beta28"]
                   shuffle
                   (sort version/version-compare)))))

(deftest newer?-test
  (is (= false (version/newer? "1" "2")))
  (is (= false (version/newer? "2" "2")))
  (is (= true  (version/newer? "2" "1"))))

(deftest older?-test
  (is (= true  (version/older? "1" "2")))
  (is (= false (version/older? "2" "2")))
  (is (= false (version/older? "2" "1"))))

(deftest newer-or-equal?-test
  (is (= false (version/newer-or-equal? "1" "2")))
  (is (= true  (version/newer-or-equal? "2" "2")))
  (is (= true  (version/newer-or-equal? "2" "1"))))

(deftest older-or-equal?-test
  (is (= true  (version/older-or-equal? "1" "2")))
  (is (= true  (version/older-or-equal? "2" "2")))
  (is (= false (version/older-or-equal? "2" "1"))))
