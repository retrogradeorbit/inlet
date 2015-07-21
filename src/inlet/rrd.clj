(ns inlet.rrd
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

(def db RrdDb)

(def AVERAGE org.rrd4j.ConsolFun/AVERAGE)
(def MAX org.rrd4j.ConsolFun/MAX)

(def GAUGE org.rrd4j.DsType/GAUGE)
(def COUNTER org.rrd4j.DsType/COUNTER)
(def DERIVE org.rrd4j.DsType/DERIVE)
(def ABSOLUTE org.rrd4j.DsType/ABSOLUTE)

(defn make-filename [host key step]
  (str "/tmp/rrd/" host "/" key ":" step ".rrd" ))

(defn add-archive [db type heartbeat min max]
  (.addArchive db type heartbeat min max))

(defn add-datasource [db label type heartbeat min max]
  (.addDatasource db label type heartbeat min max))

(defn make-rrd
  "file is the filename to store the rrd in.
  earliest is the timestamp of the earliest store.
  sources is {:label [COUNTER 600 0 200000000]
              ...}
  step is the time step
  archives is [[AVERAGE 0.5 1 86400] ...]"
  [file earliest step sources archives]
  (let [d (RrdDef. (str file) earliest step)]
    (doall
     (for [[label args] sources]
       (apply add-datasource d (name label) args)))
    (doall
     (map #(apply add-archive d %) archives))
    (RrdDb. d)))

(defn get-header [rrd]
  (let [head (.getHeader rrd)]
    {:arc-count (.getArcCount head)
     :ds-count (.getDsCount head)
     :info (.getInfo head)
     :last-update (.getLastUpdateTime head)
     :backend (.getRrdBackend head)
     :signature (.getSignature head)
     :step (.getStep head)
     :version (.getVersion head)}))

(defn fetch-data [rrd confun dsname start end]
  (let [fetch-data (.fetchData (.createFetchRequest rrd confun start end))
        values (.getValues fetch-data (name dsname))
        rows (.getRowCount fetch-data)]
    (map #(aget values %) (range rows) )))


(def layout
  {:iptabels
   {:INPUT [COUNTER 600 0 2]
    :OUTPUT [COUNTER 600 0 2]}
   :meminfo
   {:data [GAUGE 600 0 2]}})

#_ (defn make-rrd-label [file label]
  (make-new-rrd file )
  )

(defn make-new-rrd [file earliest labels step]
  (make-rrd (str file) earliest step
            {:INPUT [COUNTER 600 0 200000000]
             :OUTPUT [COUNTER 600 0 200000000]}
            [[AVERAGE 0.5 1 86400]
             [AVERAGE 0.5 60 10080]
             [AVERAGE 0.5 3600 8736]
             [AVERAGE 0.5 86400 7280]
             [MAX 0.5 1 600]]))

#_ (defn make-new-rrd [file earliest labels step]
  (let [d (RrdDef. (str file) earliest step)]
    (println "!!!" (.dump d))
    (doall
     (for [n labels]
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

(defn new-and-open [filename labels step earliest]
  (if (.exists filename)
    (RrdDb. (str filename))
    (make-new-rrd filename (dec earliest) labels step)))

(defn write-data [rrd label data]
  (doall
   (for [t (sort (keys data))]
     (let [sample (.createSample rrd)
           val (data t)]
       (.setTime sample t)
       (doall (for [k (keys val)]
                (.setTime sample k (double (get val k)))))
       (try
         (.update sample)
         (catch java.lang.IllegalArgumentException _))))))
