(ns inlet.core-test
  (:require [clojure.test :refer :all]
            [inlet.core :refer :all]))

;; (deftest a-test
;;   (testing "FIXME, I fail."
;;     (is (= 0 1))))

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

(deftest test-separate-by-labels
  (testing "separate-by-labels"
    (is
     (=
      (separate-by-labels {123 {:iptables {:input 20}}
                           234 {:iptables {:input 10}
                                :meminfo {:memory 100}}}
                          [:iptables :meminfo])
      {:iptables {123 {:input 20} 234 {:input 10}}
       :meminfo {234 {:memory 100}}}))
    (is
     (=
      (separate-by-labels {123 {:iptables {:input 20}}
                           234 {:iptables {:input 10}
                                :meminfo {:memory 100}}}
                          [:meminfo])
      {:meminfo {234 {:memory 100}}}))
    (is
     (=
      (separate-by-labels {123 {:iptables {:input 20}}
                           234 {:iptables {:input 10}
                                :meminfo {:memory 100}}}
                          [:iptables])
      {:iptables {123 {:input 20} 234 {:input 10}}}))))

(deftest test-split-sets
  (testing "split-sets"
    (is (= (split-sets {:iptables {1 {:data 1} 2 {:data 2}}
                        :extra {1 1 2 2 3 3 4 4 5 5}
                        :meminfo {1 {:data 1}}}
                       #(> (count (second %)) 1))
           [
            ;; true
            [[:iptables {1 {:data 1}, 2 {:data 2}}] [:extra {1 1, 2 2, 3 3, 4 4, 5 5}]]

            ;; false
            [[:meminfo {1 {:data 1}}]]
            ]))))
