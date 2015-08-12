(ns inlet.core
  (:require [compojure.core :refer :all]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [inlet.rrd :as rrd]
            [inlet.data :as data]
            [inlet.config :as config]
            [inlet.graph :as graph]
            [inlet.storage :as storage])
  (:use [hiccup.core]))

(def merge-conj (partial merge-with conj))

(defn process-data [{:keys [params] :as req}]
  (let [host (params "host")
        dataset (-> "data"
                    params
                    json/read-str
                    data/proc-data)
        timestamps (sort (keys dataset))
        keyset (data/get-label-set dataset)

        ;; data keyed by label. each value is time series data set
        separated (-> dataset
                      (data/separate-by-labels keyset)
                      (merge-conj @storage/=short-sets=))

        ;; long-sets will be written to rrd storage
        ;; short-sets need more data to determine the step
        [long-set short-set] (data/split-sets separated #(> (count (second %)) 2))

        ;; for each label, a sorted sequence of timestamps
        sorted-keys (into {} (for [[k v] separated] [k (sort (keys v))]))

        ;; for each label, the calculated step (difference between the lowest two timestamps).
        ;; this value is only correct if at least two data points are present
        first-two (into {} (for [[k v] separated] [k (- (apply - (take 2 (sort (keys v)))))]))

        ;; the labels and data that have more than two entries
        long-set (into {} long-set)

        ;; the labels and time data that have less than two entries
        short-set (into {} short-set)

        filenames (for [s (keys long-set)]
                    {:label s
                     :fname (rrd/make-filename host s (first-two s))
                     :step (first-two s)
                     :data (separated s)
                     })
        rrds
        (into {}
              (for [{:keys [label fname step data]} filenames]
                [label (rrd/new-and-open (io/file fname)
                                         label
                                         (-> label
                                             separated sort
                                             first second
                                             keys)
                                         (-> label keyword config/config :rrd)
                                         (first (sorted-keys label)))]))
        ]
    ;; write the long-set data out to rrd
    (doall (map
            #(rrd/write-data (rrds %) % (separated %))
            (keys long-set)

            ))

    (doall (map #(.close (rrds %)) (keys long-set) ))

    ;; short-sets need to be assoced into memorised data, so that when the
    ;; next data packet arrives, we can resurrect it and it will become a long set.

    ;; everything weve stored, we purge, and then we add in the short set
    (swap! storage/=short-sets=
           #(merge-with conj
                        (apply dissoc % (keys long-set))
                        short-set))

    (println "written" (keys long-set) "not-written" (keys short-set))

    "OK"
    ))

(defn hic [host db]
  (html [:html [:head]
         [:body
          (for [dur [600 3600 86400 604800 2419200]]
            [:img#ten-minutes
             {:src
              (str
               "/image?duration="
               dur
               "&height=220&db="
               db
               "&host="
               host
               "&step=20&title=Previous+10+Minutes")}])

          [:script]
          ]]))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (GET "/image" req (graph/image-create req))
  (POST "/data" req (process-data req) )

  (GET "/:host/:db" [host db] (hic host db)))

(def app (-> app-routes
             wrap-params))

(defn -main []
  (let [{:keys [ip port rrd-path]}
        (config/find-and-load)]
    (println (str "starting server on " ip ":" port))
    (run-server app {:port port :ip ip})))



(comment
                                 "
<img id='ten-mins' src='/image?duration=600&height=220&db=%2$s&host=%1$s&step=20&title=Previous+10+Minutes'/>
<img id='hour' src='/image?duration=3600&height=140&db=%2$s&host=%1$s&step=20&title=Previous+Hour'/>
<img id='day' src='/image?duration=86400&height=100&db=%2$s&host=%1$s&step=20&title=Previous+Day'/>
<img src='/image?duration=604800&height=85&db=%2$s&host=%1$s&step=20&title=Previous+Week'/>
<img src='/image?duration=2419200&height=70&db=%2$s&host=%1$s&step=20&title=Previous+Month'/>
<img src='/image?duration=31449600&height=55&db=%2$s&host=%1$s&step=20&title=Previous+Year'/>
<script type='text/javascript'>
function update() {
  document.getElementById('hour').src = '/image?duration=3600&height=140&db=%2$s&host=%1$s&step=20&title=Previous+Hour&time=' + new Date();
  document.getElementById('ten-mins').src = '/image?duration=600&height=220&db=%2$s&host=%1$s&step=20&title=Previous+10+Minutes&time=' + new Date();
}

window.setInterval(update, 5000);

</script>

")
