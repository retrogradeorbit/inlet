(ns inlet.core-test
  (:require [clojure.test :refer :all]
            [inlet.core :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
(deftest test-get-label-set
  (testing "get-label-set"
    (is
     (=
      (get-label-set {123 {:iptables {:input 10}}
                      234 {:iptables {:input 10}}})
      #{:iptables}))
    (is
     (=
      (get-label-set {123 {:iptables {:input 10}}
                      234 {:iptables {:input 10}
                           :meminfo {:memory 100}}})
      #{:iptables :meminfo}))))

