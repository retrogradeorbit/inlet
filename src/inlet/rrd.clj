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

;; Consolidation functions
(def AVERAGE org.rrd4j.ConsolFun/AVERAGE)
(def MIN org.rrd4j.ConsolFun/MIN)
(def MAX org.rrd4j.ConsolFun/MAX)
(def LAST org.rrd4j.ConsolFun/LAST)
(def FIRST org.rrd4j.ConsolFun/FIRST)
(def TOTAL org.rrd4j.ConsolFun/TOTAL)

;; Datasource types
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

(defn get-header
  "Turn an RRD header into a clojure hashmap"
  [rrd]
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

(defn make-color
  ([rgb] (Color. rgb))
  ([rgb a] (Color. rgb a))
  ([r g b] (Color. r g b))
  ([r g b a] (Color. r g b a)))

(defn make-graph [{:keys [width height filename start end
                          title vertical-label draw data format
                          back-make-color canvas-make-color
                          major-grid-make-color grid-make-color
                          rrd cdefs defs]
                   :or {width 900
                        height 200
                        title "RRD Graph"
                        vertical-label "units"
                        format "png"
                        draw []
                        data []
                        back-make-color [0xffffff]
                        canvas-make-color [0xfff8f8]
                        major-grid-make-color [0x500000]
                        grid-make-color [0xa0a0a0]
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
      (.setColor RrdGraphConstants/COLOR_SHADEA (apply make-color back-make-color))
      (.setColor RrdGraphConstants/COLOR_SHADEB (apply make-color back-make-color))

      ;; background frame
      (.setColor RrdGraphConstants/COLOR_BACK (apply make-color back-make-color))

      ;; graph background
      (.setColor RrdGraphConstants/COLOR_CANVAS (apply make-color canvas-make-color))

      ;; major grid
      (.setColor RrdGraphConstants/COLOR_MGRID (apply make-color major-grid-make-color))

      ;; frame border and minor grid
      (.setColor RrdGraphConstants/COLOR_GRID (apply make-color grid-make-color))

      ;; minor grid
      (.setNoMinorGrid true)
      (.setAntiAliasing true)
      (.setShowSignature false)
      (.setNoLegend false))

    (println defs)
    (println cdefs)


    (doseq
        [{:keys [label datapoint func]} defs]
      (.datasource rrdgdef label rrd datapoint func))

    (doseq
        [{:keys [label rpn]} cdefs]
      (.datasource rrdgdef label rpn))

    (doseq
        [{:keys [type color label]} draw]
      (case type
        :area (.area rrdgdef (name label) (apply make-color color) (name label))
        :stack (.stack rrdgdef (name label) (apply make-color color) (name label))
        :line (.line rrdgdef (name label) (apply make-color color) (name label))))

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
    (make-new-rrd filename type earliest labels step)))

(defn write-data [^org.rrd4j.core.RrdDb rrd label data]
  {:pre [(or (keyword? label) (string? label)) (keys data)]}
  (doall
   (for [t (sort (keys data))]
     (let [sample (.createSample rrd)
           val (data t)]
       (.setTime sample t)
       (doall
        (for [k (keys val)]
          (.setValue sample (name k) (double (get val k)))))

       (try
         (.update sample)
         (catch java.lang.IllegalArgumentException _ (println "Sample::update raised:" _)))))))
