(ns inlet.core
  (:require [compojure.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [inlet.rrd :as rrd]
            [inlet.data :as data]
            [inlet.storage :as storage]))

(def merge-conj (partial merge-with conj))

(defn process-data [{:keys [params] :as req}]
  (let [host (params "host")
        dataset (-> "data"
                 params
                 json/read-str
                 data/proc-data)
        timestamps (sort (keys dataset))
        keyset (data/get-label-set dataset)

        ;; data keyed by label. each value is time series data set
        separated (-> dataset
                      (data/separate-by-labels keyset)
                      (merge-conj @storage/=short-sets=))

        ;; long-sets will be written to rrd storage
        ;; short-sets need more data to determine the step
        [long-set short-set] (data/split-sets separated #(> (count (second %)) 2))

        ;; for each label, a sorted sequence of timestamps
        sorted-keys (into {} (for [[k v] separated] [k (sort (keys v))]))

        ;; for each label, the calculated step (difference between the lowest two timestamps).
        ;; this value is only correct if at least two data points are present
        first-two (into {} (for [[k v] separated] [k (- (apply - (take 2 (sort (keys v)))))]))

        ;; the labels and data that have more than two entries
        long-set (into {} long-set)

        ;; the labels and time data that have less than two entries
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

    (doall (map #(.close (rrds %)) (keys long-set) ))

    ;; check the reads
    (comment
      (println "=>" (first  (sorted-keys "iptables")) (last (sorted-keys "iptables")))
      (println rrds)
      (when (some #(= (first %) "iptables") rrds)
        (println "FETCH=>" (rrd/fetch-data (rrds "iptables")
                                        ;(rrd/load-db "/tmp/rrd/knives/iptables:1.rrd")
                                           rrd/AVERAGE
                                           "OUTPUT"
                                           (first (sorted-keys "iptables"))
                                           (last (sorted-keys "iptables"))
                                           ))))


    ;; short-sets need to be assoced into memorised data, so that when the
    ;; next data packet arrives, we can resurrect it and it will become a long set.

    ;; everything weve stored, we purge
    (println "removing" (for [[k v] long-set] [k (count v)]))
    (swap! storage/=short-sets= (fn [old] (apply dissoc old (keys long-set))))

    ;; add in the short set
    (println "adding" (for [[k v] short-set] [k (count v)]))
    (swap! storage/=short-sets= #(merge-with conj % short-set))
    ;(println "!!!!Atom:" @storage/=short-sets=)

    (println "written" (keys long-set) "not-written" (keys short-set))

    "OK"
    ))


(defn now [] (int (/ (.getTime (new java.util.Date)) 1000)))
;(future-cancel grapher)

(def periods
  [["hour" (* 60 60) 300]
   ["day" (* 24 60 60) 250]
   ["week" (* 7 24 60 60) 200]
   ["month" (* 4 7 24 60 60) 120]
   ["year" (* 52 7 24 60 60) 60]])

(defn build-graph-series
  [title output-base drawset]
  (doall (map (fn [[period period-time period-height]]
                (rrd/make-graph
                 {:title (str title "(last " period ")")
                  :filename (str output-base "-" period ".png")
                  :height period-height
                  :start (- (now) period-time)
                  :end (now)
                  :draw drawset}))
              periods)))


(comment
  (def grapher (future
                 (loop []
                   (Thread/sleep 1000)

                   (when (.exists (io/file "/tmp/rrd/knives/meminfo:20.rrd"))
                     (build-graph-series "Meminfo @ knives"
                                         "/tmp/meminfo"
                                         [
                                          {:datasource ["memtotal"
                                                        "/tmp/rrd/knives/meminfo:20.rrd"
                                                        :MemTotal rrd/AVERAGE]
                                           :chart [:area 0x70 0x00 0x00 "MemTotal"]}
                                          {:datasource ["memfree"
                                                        "/tmp/rrd/knives/meminfo:20.rrd"
                                                        :MemFree rrd/AVERAGE]
                                           :chart [:area 0xd0 0x60 0x60 "MemFree"]}
                                          ])
                     )
                   (when (.exists (io/file "/tmp/rrd/knives/iptables:1.rrd"))
                     (build-graph-series "Traffic @ knives"
                                         "/tmp/iptables"
                                         [
                                          {:datasource ["input"
                                                        "/tmp/rrd/knives/iptables:1.rrd"
                                                        :INPUT rrd/AVERAGE]
                                           :chart [:area 0xd0 0x60 0x60 "Input"]}
                                          {:datasource ["output"
                                                        "/tmp/rrd/knives/iptables:1.rrd"
                                                        :OUTPUT rrd/AVERAGE]
                                           :chart [:area 0x70 0x00 0x00 "Output"]}
                                          ]))

                   (recur)))))


  (let [graph (rrd/make-graph
               {:title "Test"

                ;; create graph in memory
                ;; http://rrd4j.googlecode.com/svn/trunk/javadoc/reference/org/rrd4j/graph/RrdGraphDef.html#setFilename(java.lang.String)
                :filename "-"

                :height height
                :start (- (now) time)
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
                       ]})
        info (.getRrdGraphInfo graph)]
    {:status 200
     :headers {"Content-Type" "image/png"}
     :body (-> info
               .getBytes
               io/input-stream)}))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/image" req (image-create 400 10000))
  (POST "/data" req (process-data req) )
  (GET "/meminfo-year.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/meminfo-year.png")})
  (GET "/meminfo-month.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/meminfo-month.png")} )
  (GET "/meminfo-week.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                             :body (io/file "/tmp/meminfo-week.png")} )
  (GET "/meminfo-day.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/meminfo-day.png")} )
  (GET "/meminfo-hour.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/meminfo-hour.png")} )
  (GET "/meminfo" [] "<img src='/meminfo-hour.png'/><img src='/meminfo-day.png'/>
<img src='meminfo-week.png'/><img src='meminfo-month.png'/><img src='meminfo-year.png'/>")
   (GET "/iptables-year.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/iptables-year.png")})
  (GET "/iptables-month.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/iptables-month.png")} )
  (GET "/iptables-week.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                             :body (io/file "/tmp/iptables-week.png")} )
  (GET "/iptables-day.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/iptables-day.png")} )
  (GET "/iptables-hour.png" [] {:status 200
                               :headers {"Content-Type" "image/png"}
                               :body (io/file "/tmp/iptables-hour.png")} )
  (GET "/iptables" [] "<img id='hour' src='/iptables-hour.png'/><img id='day' src='/iptables-day.png'/>
<img src='iptables-week.png'/><img src='iptables-month.png'/><img src='iptables-year.png'/>
<script type='text/javascript'>
function update() {
  document.getElementById('day').src = '/iptables-day.png?time=' + new Date();
  document.getElementById('hour').src = '/iptables-hour.png?time=' + new Date();
}

window.setInterval(update, 5000);

</script>
")

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
