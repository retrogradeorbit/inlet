(ns inlet.graph
  (:require [inlet.rrd :as rrd]
            [clojure.java.io :as io]))

(def config
  {:iptables
   {:step 1
    :args {:canvas-color [0xffffff]
           :major-grid-color [0x00 0x00 0x00 0x20]}

    :defs [
           {:label :input
            :datapoint :INPUT
            :func rrd/AVERAGE}
           {:label :output
            :datapoint :OUTPUT
            :func rrd/AVERAGE}
           ]

    :cdefs []

    :draw [
           {:type :area
            :color [0x70 0x00 0x00]
            :label :input
            :legend "Inbound"}
           {:type :area
            :color [0xd0 0x60 0x60]
            :label :output
            :legend "Outbound"}]}

   :meminfo
   {
    :step 20

    :args {:canvas-color [0xffffff]
           :major-grid-color [0x00 0x00 0x00 0x20]
           :min-value 0}

    :defs
    [
     {:label :free
      :datapoint :MemFree
      :func rrd/AVERAGE}
     {:label :total
      :datapoint :MemTotal
      :func rrd/AVERAGE}
     ]

    :cdefs
    [
     {:label :used
      :rpn "total,free,-"
      }
     ]

    :draw
    [
     {:type :area
      :color [0xff 0x00 0x00 0x80]
      :label :used
      :legend "Used Memory"}
     {:type :stack
      :color [0xEC 0xD7 0x48 0x80]
      :label :free
      :legend "Free Memory"
      }]}})

(defn now [] (int (/ (.getTime (new java.util.Date)) 1000)))

(defn image-create [{{:strs [duration height host db title]
                      :or {duration "10000"
                           height "200"
                           host "knives"
                           db "meminfo"
                           title "This is the default title"
                           }
                      :as params} :params}]
  (let [{:keys [step draw data args
                cdefs defs]} (config (keyword db))
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
