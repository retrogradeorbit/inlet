(ns inlet.core
  (:require [compojure.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]]
            ;; [rrd4clj.io :as io]
            ;; [rrd4clj.core :as rrd]
            ;; [rrd4clj.graph :as g]
            ))

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
