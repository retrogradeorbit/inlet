(ns inlet.config-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [inlet.config :as config]))

(deftest test-find-first
  (testing "find-first"
    (is (= "."
           (config/find-first ["dfdfgdsg" "sdfgsdfg" "."])))))
