(ns inlet.graph
  (:require [inlet.rrd :as rrd]
            [inlet.config :refer [config]]
            [clojure.java.io :as io]))

(defn now [] (int (/ (.getTime (new java.util.Date)) 1000)))

(defn image-create [{{:strs [duration height host db title]
                      :or {duration "10000"
                           height "200"
                           host "knives"
                           db "meminfo"
                           title "This is the default title"
                           }
                      :as params} :params}]
  (let [{:keys [rrd draw data args
                cdefs defs]} (-> db keyword config)
                step (:step rrd)
        present (now)
        graph (rrd/make-graph
               (into {:title title

                      ;; create graph in memory
                      ;; http://rrd4j.googlecode.com/svn/trunk/javadoc/reference/org/rrd4j/graph/RrdGraphDef.html#setFilename(java.lang.String)
                      :filename "-"

                      :height (Integer. height)
                      :start (- present (Integer. duration))
                      :end present

                      :rrd (rrd/make-filename host db step)
                      :cdefs cdefs
                      :defs defs
                      :draw draw}
                     args))
        info (.getRrdGraphInfo graph)]
    {:status 200
     :headers {"Content-Type" "image/png"}
     :body (-> info
               .getBytes
               io/input-stream)}))
