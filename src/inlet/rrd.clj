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

(defn load-db [filename]
  (RrdDb. (str filename)))

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

(defn make-graph [{:keys [width height filename start end
                          title vertical-label draw format]
                   :or {width 900
                        height 200
                        title "RRD Graph"
                        vertical-label "units"
                        format "png"
                        draw []
                        }}]
  (let [rrdgdef (RrdGraphDef.)]
    (doto rrdgdef
      (.setWidth width)
      (.setHeight height)
      (.setFilename filename)
      (.setStartTime start)
      (.setEndTime end)
      (.setTitle title)
      (.setVerticalLabel vertical-label)

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
      (.setNoLegend false))

    (doall
     (for [{[ident rrdfile datasource consfunc] :datasource
            [type r g b desc] :chart}
           draw]
       (do
         (.datasource rrdgdef ident rrdfile (name datasource) consfunc)
         (case type
           :area (.area rrdgdef ident (Color. r g b) desc)))))

    (.setImageFormat rrdgdef format)
    (RrdGraph. rrdgdef)))


(def layout
  {:iptables [COUNTER 600 0 2200000000]
   :meminfo [GAUGE 600 0 Double/NaN]})

(defn make-new-rrd [file type earliest labels step]
  (println "MAKE-NEW-RRD" file type earliest labels step)
  (let [datasources
        (into {} (for [n labels] [(name n) (layout (keyword type))]))]
    (println "DataSources" datasources)
    (make-rrd (str file) earliest step
              datasources
              [[AVERAGE 0.5 1 86400]
               [AVERAGE 0.5 60 10080]
               [AVERAGE 0.5 3600 8736]
               [AVERAGE 0.5 86400 7280]
               [MAX 0.5 1 600]])))


(defn new-and-open [filename type labels step earliest]
  (if (.exists filename)
    (RrdDb. (str filename))
    (make-new-rrd filename type (dec earliest) labels step)))

(defn write-data [^org.rrd4j.core.RrdDb rrd label data]
  {:pre [(or (keyword? label) (string? label)) (keys data)]}
  (println "write-data" rrd label data)
  (doall
   (for [t (sort (keys data))]
     (let [sample (.createSample rrd)
           val (data t)]
       (.setTime sample t)
       (doall (for [k (keys val)]
                (do (println "writing sample:" t (name k) (double (get val k)))
                    (.setValue sample (name k) (double (get val k))))))

       ;(.update sample)

       (try
         (println "update returned:" (.update sample))
         (catch java.lang.IllegalArgumentException _ (println "EXC RAISED:" _)))
       ))))
