(ns inlet.core
  (:require [compojure.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [org.rrd4j.core RrdDef RrdDb Sample]
           [org.rrd4j.graph  RrdGraph RrdGraphDef RrdGraphConstants]
           [org.rrd4j.ConsolFun]
           [org.rrd4j.DsType]
           [java.awt Color]))

(def AVERAGE org.rrd4j.ConsolFun/AVERAGE)
(def MAX org.rrd4j.ConsolFun/MAX)

(def GAUGE org.rrd4j.DsType/GAUGE)
(def COUNTER org.rrd4j.DsType/COUNTER)
(def DERIVE org.rrd4j.DsType/DERIVE)
(def ABSOLUTE org.rrd4j.DsType/ABSOLUTE)

(def step 1)

(comment

  (def rrd (RrdDef. "/tmp/test.rrd" step))
  (doto rrd

    ;; name, type, heartbeat, min, max
    (.addDatasource "probe-1-temp" GAUGE 600 Double/NaN Double/NaN)
    (.addDatasource "probe-2-temp" GAUGE 600 Double/NaN Double/NaN)

    ;; Consolidation Func, XFilesFactor Steps Rows
    (.addArchive AVERAGE 0.5 1 (/ (* 24 60 60) step))   ;; one days worth @ 1 sec
    (.addArchive AVERAGE 0.5 60 (/ (* 24 60 7) step))   ;; 7 days @ 1 min
    (.addArchive AVERAGE 0.5 (* 60 60) (/ (* 24 7 52) step)) ;; one year @ 1 hour
    (.addArchive AVERAGE 0.5 (* 60 60 24) (/ (* 7 52 20) step)) ;; 20 years @ 1 day
    (.addArchive MAX 0.5 1 600))

  (def rdb (RrdDb. rrd))

  (def sample (.createSample rdb))
  (.setTime sample 0)
  (.setValue sample "probe-1-temp" (double 60))

  ;; writes the file
  (.close rdb)


  ;; make a graph
  (def rrdgdef (RrdGraphDef.))
  (doto rrdgdef
    (.setWidth 800)
    (.setHeight 150)
    (.setFilename "/tmp/test.png")
    (.setStartTime 0)
    (.setEndTime 1437105502)
    (.setTitle "My Title")
    (.setVerticalLabel "bytes")
    (.datasource "bytes" "/tmp/test.rrd" "probe-1-temp" AVERAGE)
    (.hrule 2568 Color/GREEN "hrule")
    (.setImageFormat "png"))
  (def graph  (RrdGraph. rrdgdef))

)



(defn filename [host]
  (str "/tmp/rrd/" host ".rrd"))

(defn make-new-rrd [file earliest first-data]
  (let [d (RrdDef. (str file) earliest 1)
        step 1]
    (println "!!!" (.dump d))
    (doall
     (for [n (keys (first-data "iptables"))]
       (do
         (println "name:" n)
         (.addDatasource d n COUNTER 600 0 200000000))))
    (doto d
      (.addArchive AVERAGE 0.5 1 (/ (* 24 60 60) step)) ;; one day @ 1 sec
      (.addArchive AVERAGE 0.5 60 (/ (* 24 60 7) step)) ;; 7 days @ 1 min
      (.addArchive AVERAGE 0.5 (* 60 60) (/ (* 24 7 52) step)) ;; one year @ 1 hour
      (.addArchive AVERAGE 0.5 (* 60 60 24) (/ (* 7 52 20) step)) ;; 20 years @ 1 day
      (.addArchive MAX 0.5 1 600))
    (RrdDb. d)))

(defn make-graph [file imagefile earliest latest]
  (let [rrdgdef (RrdGraphDef.)]
    (doto rrdgdef
      (.setWidth 900)
      (.setHeight 200)
      (.setFilename imagefile)
      (.setStartTime earliest)
      (.setEndTime latest)
      (.setTitle "Firewall Traffic")
      (.setVerticalLabel "bytes")

      ;; hide bevel
      (.setColor RrdGraphConstants/COLOR_SHADEA Color/WHITE)
      (.setColor RrdGraphConstants/COLOR_SHADEB Color/WHITE)

      ;; background frame
      (.setColor RrdGraphConstants/COLOR_BACK Color/WHITE)

      ;; graph background
      (.setColor RrdGraphConstants/COLOR_CANVAS (Color. 0xf8 0xf8 0xff))

      ;; major grid
      (.setColor RrdGraphConstants/COLOR_MGRID (Color. 0x50 0x00 0x00))

      ;; frame border and minor grid
      (.setColor RrdGraphConstants/COLOR_GRID (Color. 0xa0 0xa0 0xa0))

      ;; minor grid
      (.setNoMinorGrid true)

      (.setAntiAliasing true)

      (.setShowSignature false)

      (.setNoLegend false)



      ;; (.setAltAutoscale true)
      ;; (.setAltAutoscaleMin false)
      ;; (.setAltAutoscaleMax false)

      ;(.setMinValue 0)
      ;(.setMaxValue 1000000)

      (.datasource "input" file "INPUT" AVERAGE)
      ;(.area "input" (Color. 0xd0 0x60 0x60 ) "Firewall Input Chain")
      (.area "input" (Color. 0x90 0x90 0xe0 ) "Firewall Input Chain")
      (.datasource "output" file "OUTPUT" AVERAGE)
      ;(.area "output" (Color. 0x70 0x00 0x00) "Firewall Output Chain")
      (.area "output" (Color. 0x00 0x00 0x70) "Firewall Output Chain")
      ;(.hrule 0.6  Color/GREEN "hrule")
      (.setImageFormat "png"))
    (RrdGraph. rrdgdef)))

(defn proc-data [data]
  (into {}
        (for [[k v] data]
          [(/ (read-string k) 1000) v])
        ))

(defn process-data [{:keys [params] :as req}]
  (let [host (params "host")
        data (-> "data"
                 params
                 json/read-str
                 proc-data)
        timestamps (sort (keys data))
        keyset (reduce
                (fn [acc x]
                  (apply conj acc x))
                #{}
                (map (comp keys second) data))
        separated (into
                   {}
                   (for [k keyset]
                     [k (into
                         {}
                         (filter #(second %)
                                 (for [t timestamps]
                                   [t ((data t) k)])))]))

        counts (into {} (for [[k v] separated] [k (count v)] ))
        {long-set true
         short-set false} (group-by #(> (second %) 2000) counts)
        ;sorted-keys (for [[k v] separated] [k (sort (keys v))])

        first-two (into {} (for [[k v] separated] [k (- (apply - (take 2 (sort (keys v)))))]))

        long-set (into {} long-set)
        short-set (into {} short-set)
        filename (for [s (keys long-set)]
                   {:filename (str "/tmp/rrd/" host "/" s ":" (first-two s) ".rrd" )
                    :name s
                    :step (first-two s)
                    :data (separated s)})

        ]
    (println "---------")
    (println filename)
    (println short-set "<<<" long-set)
    (pprint first-two)))


(defn data [{:keys [params] :as req}]
  ;(println "data" req)
  (let [host (params "host")
        data (-> "data"
                 params
                 json/read-str
                 proc-data)
        timestamps (sort (keys data))


        earliest (first timestamps)
        first-data (data earliest)
        nearliest (second timestamps)
        latest (last timestamps)
        step (- nearliest earliest)
        file (io/file (filename host))
        ]

    (let [rrd (if (.exists file)
                (RrdDb. (str file))
                (make-new-rrd file (dec earliest) first-data))]
      (doall
       (for [t timestamps]
         (let [sample (.createSample rrd)]
           (.setTime sample  t)
           (.setValue sample "INPUT" (double (get-in data [t "iptables" "INPUT"])) )
           (.setValue sample "OUTPUT" (double (get-in data [t "iptables" "OUTPUT"])) )
           (try
             (.update sample)
             (catch java.lang.IllegalArgumentException _))))
       )
      (.close rrd)
      (println rrd "," host ":" earliest "->" latest "." step)

      ;; (make-graph (str file) (- latest 2000 ;00
      ;;                           ) latest)


      "OK")))


(comment
  (defn now [] (/ (.getTime (new java.util.Date)) 1000))
  (def now )
  (def grapher (future
                 (loop []
                   (Thread/sleep 1000)
                   (make-graph "/tmp/rrd/knives.rrd" (- (now) 500) (now))
                   (recur))))

  (future-cancel grapher)
  )

(defn now [] (/ (.getTime (new java.util.Date)) 1000))
;(future-cancel grapher)

(def grapher (future
               (loop []
                 (Thread/sleep 1000)
                 ;(println "graph")
                 (make-graph "/tmp/rrd/knives.rrd" "/tmp/knives-1.png" (- (now) 3000) (now))
                 (make-graph "/tmp/rrd/knives.rrd" "/tmp/knives-2.png"
                             (- (now) 1000) (now))
                 (recur))))


(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/data" req (data req) )
  )

(def app (-> app-routes
             wrap-params))

(defn -main []
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
