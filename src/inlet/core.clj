(ns inlet.core
  (:require [compojure.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]])
  (:import [org.rrd4j.core RrdDef RrdDb Sample]
           [org.rrd4j.ConsolFun]
           [org.rrd4j.DsType]))

(def AVERAGE org.rrd4j.ConsolFun/AVERAGE)
(def MAX org.rrd4j.ConsolFun/MAX)

(def GAUGE org.rrd4j.DsType/GAUGE)
(def COUNTER org.rrd4j.DsType/COUNTER)
(def DERIVE org.rrd4j.DsType/DERIVE)
(def ABSOLUTE org.rrd4j.DsType/ABSOLUTE)

(comment
  (def rrd (RrdDef. "/tmp/test.rrd" 300))
  (doto rrd

    ;; name, type, heartbeat, min, max
    (.addDatasource "probe-1-temp" GAUGE 600 55 95)

    ;; Consolidation Func, XFilesFactor Steps Rows
    (.addArchive AVERAGE 0.5 1 600)
    (.addArchive AVERAGE 0.5 6 700)
    (.addArchive MAX 0.5 1 600))

  (def rdb (RrdDb. rrd))

  (def sample (.createSample rdb))
  (.setTime sample 0)

  ;; some reason this next one gives:
  ;; No matching method found: setValue for class org.rrd4j.core.Sample
  ;; IllegalArgumentException No matching method found: setValue for class org.rrd4j.core.Sample  clojure.lang.Reflector.invokeMatchingMethod (Reflector.java:80)
  (.setValue sample "probe-1-temp" 60)


)


(defn data [req]
  (pprint req)
  "OK"
  )

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
