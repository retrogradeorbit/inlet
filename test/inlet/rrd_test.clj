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
    (let [rrd-file "/tmp/test.rrd"
            rrd
            (make-rrd rrd-file 140000 1
                      {:INPUT [COUNTER 600 0 200000000]
                       "OUTPUT" [COUNTER 600 0 200000000]}
                      [[AVERAGE 0.5 1 86400]
                       [AVERAGE 0.5 60 10080]
                       [AVERAGE 0.5 3600 8736]
                       [AVERAGE 0.5 86400 7280]
                       [MAX 0.5 1 600]])]
        (is (.exists (io/file rrd-file)))
        (is (> (.length (io/file rrd-file)) 1000000))
        (let [{:keys [arc-count ds-count last-update step]}
              (get-header rrd)]
          (is (= 5 arc-count))
          (is (= 2 ds-count))
          (is (= 140000 last-update))
          (is (= 1 step))))))


(deftest test-write-data
  (testing "write-data"
    (let [rrd-file "/tmp/test.rrd"
          rrd
          (make-rrd rrd-file 140000 1
                    {:INPUT [COUNTER 600 0 200000000]
                     :OUTPUT [COUNTER 600 0 200000000]}
                    [[AVERAGE 0.5 1 86400]
                     [AVERAGE 0.5 60 10080]
                     [AVERAGE 0.5 3600 8736]
                     [AVERAGE 0.5 86400 7280]
                     [MAX 0.5 1 600]])]
      (write-data rrd :iptables {140001 {:INPUT 100 :OUTPUT 200}
                                 140002 {:INPUT 101 :OUTPUT 202}})
      (is)
      )))
