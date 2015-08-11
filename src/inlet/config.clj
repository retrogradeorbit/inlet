(ns inlet.config
  (:require [inlet.rrd :as rrd]
            [clojure.java.io :as io]))

(def config
  {:iptables
   {
    :rrd {:step 1
          :layout [rrd/COUNTER 600 0 2200000000]
          :stores [[rrd/AVERAGE 0.5 1 86400]
                   [rrd/AVERAGE 0.5 60 10080]
                   [rrd/AVERAGE 0.5 3600 8736]
                   [rrd/AVERAGE 0.5 86400 7280]
                   [rrd/MAX 0.5 1 600]]}

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
    :rrd {:step 20
          :meminfo [rrd/GAUGE 600 0 Double/NaN]
          :stores [[rrd/AVERAGE 0.5 1 86400]
                   [rrd/AVERAGE 0.5 60 10080]
                   [rrd/AVERAGE 0.5 3600 8736]
                   [rrd/AVERAGE 0.5 86400 7280]
                   [rrd/MAX 0.5 1 600]]}

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

(def default-config
  {:ip "0.0.0.0"
   :port 5000

   :rrd-path "/tmp/inlet"
   })

(def config-search-path
  ["~/.inlet.clj" "~/.inlet/inlet.clj" "/etc/inlet.clj" "inlet.clj"])

(defn find-first [paths]
  (->> paths
       (map #(when (-> % io/file .exists) %))
       (remove nil?)
       first))

(defn load-config [filename]
  (-> filename slurp read-string))

(def merge-into (partial merge-with into))

(defn find-and-load []
  (let [fname (find-first config-search-path)]
    (if fname
      (merge-with into default-config (load-config fname))
      default-config)))
