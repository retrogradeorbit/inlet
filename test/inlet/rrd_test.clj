(ns inlet.rrd-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [inlet.rrd :refer :all]))

(deftest test-get-label-set
  (testing "make-filename"
    (is (=
         (make-filename "hostname" "iptables" 1)
         "/tmp/rrd/hostname/iptables:1.rrd"))))

(deftest test-make-rrd
  (testing "make-rrd"
    (is (let [rrd
              (make-rrd "/tmp/test.rrd" 140000 1
                        {:INPUT [COUNTER 600 0 200000000]
                         "OUTPUT" [COUNTER 600 0 200000000]}
                        [[AVERAGE 0.5 1 86400]
                         [AVERAGE 0.5 60 10080]
                         [AVERAGE 0.5 3600 8736]
                         [AVERAGE 0.5 86400 7280]
                         [MAX 0.5 1 600]])]
          (.exists (io/file "/tmp/test.rrd"))))))
