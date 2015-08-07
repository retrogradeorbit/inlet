(ns inlet.rrd-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [inlet.rrd :refer :all]))

(deftest test-get-label-set
  (testing "make-filename"
    (is (=
         (make-filename "hostname" "iptables" 1)
         "/tmp/rrd/hostname/iptables:1.rrd"))))

(deftest test-make-folders
  (testing "make-folders"
    (is (make-containing-folders "/tmp/a/b/c/d/e"))
    (is (-> "/tmp/a/b/c/d"
            io/file
            .exists))
    (is (-> "/tmp/a/b/c/d"
            io/file
            io/delete-file))))

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
                                 140002 {:INPUT 101 :OUTPUT 202}
                                 140003 {:INPUT 105 :OUTPUT 204}})
      (is (= 140003 (:last-update (get-header rrd))))

      ;; counters store diffs, so theses are the diffs of the data
      (is (= '(1.0 4.0) (fetch-data rrd AVERAGE :INPUT 140002 140003)))
      (is (= '(2.0 2.0) (fetch-data rrd AVERAGE :OUTPUT 140002 140003)))

      (let [graph-file "-"
            graph
            (make-graph
             {:title "Test Graph"
              :filename graph-file
              :start 140000
              :end 140010
              :rrd rrd-file

              :defs [
                     {:label :input
                      :datapoint :INPUT
                      :func AVERAGE}
                     {:label :output
                      :datapoint :OUTPUT
                      :func AVERAGE}
                     ]

              :cdefs []

              :draw [
                     {:type :area
                      :color [0x70 0x00 0x00]
                      :label :input
                      :legend "Inbound"}
                     {:type :area
                      :color [0xd0 0x60 0x60]
                      :label :output
                      :legend "Outbound"}]

              })

            info (.getRrdGraphInfo graph)
            size (-> info .getByteCount)]
        (is (> size 3000))))))
