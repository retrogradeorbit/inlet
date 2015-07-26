(ns inlet.storage
  (:require [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clj-time.format :as format]
            [clj-time.coerce :as coerce]
            [clojure.edn :as edn]))

(def ^:dynamic *image-storage-path* "/tmp/inlet")
(def ^:dynamic *time-format* "yyyy-MM-dd-HH:mm:ss.SSS")
(def ^:dynamic *time-re* #"data-(\d+\-\d+\-\d+-\d+:\d+:\d+\.\d+).edn")

(defn init-storage!
  ([]
   (init-storage! *image-storage-path*))
  ([path]
   (when-not (fs/exists? path)
     (when-not (fs/mkdir path)
       (throw (ex-info (str "Can't create storage path:" path)
                       {:type ::fs-exception}))))))

(defn make-filename [timestamp]
  (->> (str "data-" (-> *time-format*
                        format/formatter
                        (format/unparse timestamp)) ".edn")
       (io/file *image-storage-path*)))

(defn get-filenames []
  (let [items
        (filter seq
                (map
                 #(->> %
                       str
                       (re-seq *time-re*)
                       first
                       reverse)
                 (fs/list-dir *image-storage-path*)))

        fname-parse (fn [[ind fname]]
                      [(-> *time-format*
                           format/formatter
                           (format/parse ind))
                       fname])]
    (into
     {}
     (map fname-parse items))))

(defn load-edn [filename]
  (edn/read-string (slurp (io/file *image-storage-path* filename))))
