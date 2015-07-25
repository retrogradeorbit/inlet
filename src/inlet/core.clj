(ns inlet.core
  (:require [compojure.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [inlet.rrd :as rrd]))


(defn proc-data [data]
  (into {}
        (for [[k v] data]
          [(/ (read-string k) 1000) v])
        ))


(defn get-label-set
  "Take some data first keyed by timestamp, then
  by label, and returns the set of all featured labels.

  so: {1234 {:iptables {:input 34 ...}...}...}
  and looks at every timestamp and its child labels
  (eg :iptables here) and returns a set of all
  labels such as #{:iptables}"
  [data]
  (reduce
   (fn [acc x]
     (apply conj acc x))
   #{}
   (map (comp keys second) data)))

(defn separate-by-labels
  "Take the data, and a series of labels,
  and extract all the data in any timestamp
  by that label. Then key all that data inside
  the label by timestamp. ie output is

  (-> separated :iptables 3456734)
  ;; -> the data for timestamp 3456734 label :iptables"
  [data labels]
  (into
   {}
   (for [label labels]
     [label (into
             {}
             (filter second
                     (for [t (keys data)]
                       [t ((data t) label)])))])))

(defn split-sets
  [by-label pred]
  (let [{true-set true
         false-set false} (group-by pred by-label)]
    [true-set false-set]))

;; when a POST is complete, but the data is not
;; enough to determine the step, the remaining
;; data lives in this atom
(def =short-sets= (atom {}))

(def merge-conj (partial merge-with conj))

(defn process-data [{:keys [params] :as req}]
  (let [host (params "host")
        data (-> "data"
                 params
                 json/read-str
                 proc-data)
        timestamps (sort (keys data))
        keyset (get-label-set data)

        ;; data keyed by label. each value is time series data set
        separated (-> data
                      (separate-by-labels keyset)
                      (merge-conj @=short-sets=))

        ;; long-sets will be written to rrd storage
        ;; short-sets need more data to determine the step
        [long-set short-set] (split-sets separated #(> (count (second %)) 3))



        sorted-keys (into {} (for [[k v] separated] [k (sort (keys v))]))

        first-two (into {} (for [[k v] separated] [k (- (apply - (take 2 (sort (keys v)))))]))

        long-set (into {} long-set)
        short-set (into {} short-set)

        filenames (for [s (keys long-set)]
                    {:label s
                     :fname (rrd/make-filename host s (first-two s))
                     :step (first-two s)
                     :data (separated s)
                     })
        rrds
        (into {}
              (for [{:keys [label fname step data]} filenames]
                [label (rrd/new-and-open (io/file fname)
                                         label
                                         (-> label
                                             separated sort
                                             first second
                                             keys)
                                         step
                                         (first (sorted-keys label)))]))
        ]
    ;; write the long sets out
    (println (map #(vector (rrds %) % (separated %)) (keys long-set)))

    (doall (map
                 #(rrd/write-data (rrds %) % (separated %))
                 (keys long-set)

                 ))

    ;(doall (map #(.close (rrds %)) (keys long-set) ))

    ;; check the reads
    (println "=>" (first  (sorted-keys "iptables")) (last (sorted-keys "iptables")))
    (println rrds)
    (when (some #(= (first %) "iptables") rrds)
      (println "FETCH=>" (rrd/fetch-data (rrds "iptables")
                                        ;(rrd/load-db "/tmp/rrd/knives/iptables:1.rrd")
                                         rrd/AVERAGE
                                         "OUTPUT"
                                         (first (sorted-keys "iptables"))
                                         (last (sorted-keys "iptables"))
                                         )))


    ;; short-sets need to be assoced into memorised data, so that when the
    ;; next data packet arrives, we can resurrect it and it will become a long set.
    ;(println "====")
    ;(println short-set)
    ;(println "====")

    ;; everything weve stored, we purge
    (println "removing" (for [[k v] long-set] [k (count v)]))
    (swap! =short-sets= (fn [old] (apply dissoc old (keys long-set))))

    ;; add in the short set
    (println "adding" (for [[k v] short-set] [k (count v)]))
    (swap! =short-sets= #(merge-with conj % short-set))
    ;(println "!!!!Atom:" @=short-sets=)

    (println "written" (keys long-set) "not-written" (keys short-set))


    "OK"
    ))


(defn now [] (int (/ (.getTime (new java.util.Date)) 1000)))
;(future-cancel grapher)

(def grapher (future
               (loop []
                 (Thread/sleep 1000)
                                        ;(println "graph")

                 (println "writing 1")
                 (when (.exists (io/file "/tmp/rrd/knives/meminfo:20.rrd"))
                   (rrd/make-graph
                    {:title "Meminfo @ knives"
                     :filename "/tmp/meminfo.png"
                     :start (- (now) 3000)
                     :end (now)
                     :draw [

                            {:datasource ["memtotal"
                                          "/tmp/rrd/knives/meminfo:20.rrd"
                                          :MemTotal rrd/AVERAGE]
                             :chart [:area 0x70 0x00 0x00 "MemTotal"]}
                            {:datasource ["memfree"
                                          "/tmp/rrd/knives/meminfo:20.rrd"
                                          :MemFree rrd/AVERAGE]
                             :chart [:area 0xd0 0x60 0x60 "MemFree"]}
                            ]}))
                 (println "writing 2")
                 (when (.exists (io/file "/tmp/rrd/knives/iptables:1.rrd"))
                   (println (now))
                   (rrd/make-graph
                    {:title "Traffic @ knives"
                     :filename "/tmp/traffic.png"
                     :start (- (now) 3000)
                     :end (now)
                     :draw [
                            {:datasource ["input"
                                          "/tmp/rrd/knives/iptables:1.rrd"
                                          :INPUT rrd/AVERAGE]
                             :chart [:area 0xd0 0x60 0x60 "Input"]}
                            {:datasource ["output"
                                          "/tmp/rrd/knives/iptables:1.rrd"
                                          :OUTPUT rrd/AVERAGE]
                             :chart [:area 0x70 0x00 0x00 "Output"]}
                            ]}))

                 (recur))))


(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/data" req (process-data req) )
  )

(def app (-> app-routes
             wrap-params))

(defn -main []
  (println "starting server on port 5000")
  (run-server app {:port 5000}))


#_ (defn test []
  (rrd/rrd "/tmp/rrd.rrd"
           :start-time 0
           :step 300
           (rrd/data-source "a" rrd/GAUGE 600 Double/NaN Double/NaN)
           (rrd/round-robin-archive rrd/AVERAGE 0.5 1 300)
           (rrd/round-robin-archive rrd/MIN 0.5 12 300)
           (rrd/round-robin-archive rrd/MAX 0.5 12 300))
  )
