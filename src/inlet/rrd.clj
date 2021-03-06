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
                          back-color canvas-color
                          major-grid-color grid-color
                          min-value max-value
                          rrd cdefs defs]
                   :or {width 900
                        height 200
                        title "RRD Graph"
                        vertical-label "units"
                        format "png"
                        draw []
                        data []
                        back-color [0xffffff]
                        canvas-color [0xfff8f8]
                        major-grid-color [0x500000]
                        grid-color [0xa0a0a0]
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
      (.setColor RrdGraphConstants/COLOR_SHADEA (apply make-color back-color))
      (.setColor RrdGraphConstants/COLOR_SHADEB (apply make-color back-color))

      ;; background frame
      (.setColor RrdGraphConstants/COLOR_BACK (apply make-color back-color))

      ;; graph background
      (.setColor RrdGraphConstants/COLOR_CANVAS (apply make-color canvas-color))

      ;; major grid
      (.setColor RrdGraphConstants/COLOR_MGRID (apply make-color major-grid-color))

      ;; frame border and minor grid
      (.setColor RrdGraphConstants/COLOR_GRID (apply make-color grid-color))

      ;; minor grid
      (.setNoMinorGrid true)
      (.setAntiAliasing true)
      (.setShowSignature false)
      (.setNoLegend false))

    (when min-value (.setMinValue rrdgdef min-value))
    (when max-value (.setMaxValue rrdgdef max-value))

    (doseq
        [{:keys [label datapoint func]} defs]
      (.datasource rrdgdef (name label) rrd (name datapoint) func))

    (doseq
        [{:keys [label rpn]} cdefs]
      (.datasource rrdgdef (name label) rpn))

    (doseq
        [{:keys [type color label legend]} draw]
      (case type
        :area
        (.area rrdgdef (name label) (apply make-color color) (or legend (name label)))

        :stack
        (.stack rrdgdef (name label) (apply make-color color) (or legend (name label)))

        :line
        (.line rrdgdef (name label) (apply make-color color) (or legend (name label)))))

    (.setImageFormat rrdgdef format)
    (RrdGraph. rrdgdef)))

(defn make-containing-folders [filename]
  (-> filename
      io/file
      .getParent
      io/file
      .mkdirs))

(defn make-new-rrd [file type earliest labels {:keys [step layout stores]}]
  (make-containing-folders file)
  (let [datasources
        (into {} (for [n labels] [(name n) (layout (keyword type))]))]
    (make-rrd (str file) earliest step
              datasources
              stores)))

(defn new-and-open [filename type labels {:keys [step layout stores]} earliest]
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
