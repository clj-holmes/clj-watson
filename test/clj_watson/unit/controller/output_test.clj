(ns clj-watson.unit.controller.output-test
  (:require
   [clj-watson.controller.output :as output]
   [clojure.test :refer [deftest is]]))

(deftest all-good-test
  (is (= (str "Dependencies scanned: 72\n"
              "Vulnerable dependencies found: 0\n")
         (with-out-str (output/final-summary {:cnt-deps-scanned 72
                                              :cnt-deps-vulnerable 0})))))

(deftest expected-severities-test
  (is (= (str "Dependencies scanned: 72\n"
              "Vulnerable dependencies found: 10 (3 Critical, 2 High, 1 Medium, 4 Low)\n")
         (with-out-str (output/final-summary {:cnt-deps-scanned 72
                                              :cnt-deps-vulnerable 10
                                              :cnt-deps-unspecified-severity 0
                                              :cnt-deps-severities [["Critical" 3]
                                                                    ["High" 2]
                                                                    ["Medium" 1]
                                                                    ["Low" 4]]})))))
(deftest unexpected-and-unspecified-severities-test
  (is (= (str "Dependencies scanned: 72\n"
              "Vulnerable dependencies found: 11 (7 Critical), Unrecognized severities: (2 Foo, 7 Bar), Unspecified severities: 7\n")
         (with-out-str (output/final-summary {:cnt-deps-scanned 72
                                              :cnt-deps-vulnerable 11 ;; garbage in, garbage out
                                              :cnt-deps-unspecified-severity 7
                                              :cnt-deps-unexpected-severities [["Foo" 2] ["Bar" 7]]
                                              :cnt-deps-severities [["Critical" 7]]})))))
