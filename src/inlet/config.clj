(ns inlet.config
  (:require [inlet.rrd :as rrd]))

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
