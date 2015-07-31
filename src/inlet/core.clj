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

    ;; everything weve stored, we purge, and then we add in the short set
    (swap! storage/=short-sets=
           #(merge-with conj
                        (apply dissoc % (keys long-set))
                        short-set))

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

(defn image-create [{{:strs [duration height host db step title data data2]
                      :or {duration "10000"
                           height "200"
                           host "knives"
                           db "meminfo"
                           step "20"
                           title "This is the default title"
                           data "MemTotal"
                           data2 "MemFree"
                           }
                      :as params} :params}]
  (let [graph (rrd/make-graph
               {:title title

                ;; create graph in memory
                ;; http://rrd4j.googlecode.com/svn/trunk/javadoc/reference/org/rrd4j/graph/RrdGraphDef.html#setFilename(java.lang.String)
                :filename "-"

                :height (Integer. height)
                :start (- (now) (Integer. duration))
                :end (now)
                :draw [
                       {:datasource ["ds1"
                                     (str "/tmp/rrd/" host "/" db ":" step ".rrd")
                                     (keyword data) rrd/AVERAGE]
                        :chart [:area 0x70 0x00 0x00 data]}
                       {:datasource ["ds2"
                                     (str "/tmp/rrd/" host "/" db ":" step ".rrd")
                                     (keyword data2) rrd/AVERAGE]
                        :chart [:area 0xd0 0x60 0x60 data2]}
                       ]})
        info (.getRrdGraphInfo graph)]
    {:status 200
     :headers {"Content-Type" "image/png"}
     :body (-> info
               .getBytes
               io/input-stream)}))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/image" req (image-create req))
  (POST "/data" req (process-data req) )

  (GET "/meminfo/graph" []
       {:status 200
        :headers {"Content-Type" "image/png"}
        :body (io/file "/tmp/meminfo-hour.png")} )
  (GET "/meminfo" [] "
<img id='ten-mins' src='/image?duration=600&height=220&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+10+Minutes'/>
<img id='hour' src='/image?duration=3600&height=140&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Hour'/>
<img id='day' src='/image?duration=86400&height=100&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Day'/>
<img src='/image?duration=604800&height=85&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Week'/>
<img src='/image?duration=2419200&height=70&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Month'/>
<img src='/image?duration=31449600&height=55&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Year'/>
<script type='text/javascript'>
function update() {
  document.getElementById('day').src = '/image?duration=86400&height=100&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Day&time=' + new Date();
  document.getElementById('hour').src = '/image?duration=3600&height=140&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+Hour&time=' + new Date();
  document.getElementById('ten-mins').src = '/image?duration=600&height=220&db=meminfo&step=20&data2=MemFree&data=MemTotal&title=Previous+10+Minutes&time=' + new Date();
}

window.setInterval(update, 5000);

</script>

")

  (GET "/iptables" [] "
<img src='/image?duration=31449600&height=50&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Year'/>
<img src='/image?duration=2419200&height=75&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Month'/>
<img src='/image?duration=604800&height=100&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Week'/>
<img id='day' src='/image?duration=86400&height=140&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Day'/>
<img id='hour' src='/image?duration=3600&height=180&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Hour'/>
<img id='ten-mins' src='/image?duration=600&height=220&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+10+Minutes'/>
<script type='text/javascript'>
function update() {
  document.getElementById('day').src = '/image?duration=86400&height=180&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Day&time=' + new Date();
  document.getElementById('hour').src = '/image?duration=3600&height=220&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+Hour&time=' + new Date();
  document.getElementById('ten-mins').src = '/image?duration=600&height=220&db=iptables&step=1&data2=OUTPUT&data=INPUT&title=Previous+10+Minutes&time=' + new Date();
}

window.setInterval(update, 5000);

</script>

"))

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
