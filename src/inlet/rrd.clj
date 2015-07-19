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

(def AVERAGE org.rrd4j.ConsolFun/AVERAGE)
(def MAX org.rrd4j.ConsolFun/MAX)

(def GAUGE org.rrd4j.DsType/GAUGE)
(def COUNTER org.rrd4j.DsType/COUNTER)
(def DERIVE org.rrd4j.DsType/DERIVE)
(def ABSOLUTE org.rrd4j.DsType/ABSOLUTE)

(def step 1)

(defn filename [host key step]
  (str "/tmp/rrd/" host "/" key ":" step ".rrd" ))

(defn make-new-rrd [file earliest labels step]
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
