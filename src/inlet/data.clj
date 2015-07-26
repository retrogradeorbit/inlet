(ns inlet.data (:require [clojure.data.json :as json]))

(defn proc-data [data]
  (into {}
        (for [[k v] data]
          [(/ (read-string k) 1000) v])
        ))

(defn get-label-set
  "Take some data first keyed by timestamp, then
  by label, and returns the set of all featured labels.

  so: {1234 {:iptables {:input 34 ...}...}...}
  and looks at every timestamp and its child labels
  (eg :iptables here) and returns a set of all
  labels such as #{:iptables}"
  [data]
  (reduce
   (fn [acc x]
     (apply conj acc x))
   #{}
   (map (comp keys second) data)))

(defn separate-by-labels
  "Take the data, and a series of labels,
  and extract all the data in any timestamp
  by that label. Then key all that data inside
  the label by timestamp. ie output is

  (-> separated :iptables 3456734)
  ;; -> the data for timestamp 3456734 label :iptables"
  [data labels]
  (into
   {}
   (for [label labels]
     [label (into
             {}
             (filter second
                     (for [t (keys data)]
                       [t ((data t) label)])))])))

(defn split-sets
  [by-label pred]
  (let [{true-set true
         false-set false} (group-by pred by-label)]
    [true-set false-set]))
