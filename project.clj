(defproject inlet "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.1.8"]
                 [http-kit "2.1.16"]

                 ;; for marking up our interface
                 [hiccup "1.0.5"]

                 ;; file system stuff
                 [me.raynes/fs "1.4.6"]

                 ;; use rrd4j directly
                 [org.rrd4j/rrd4j "2.2.1"]

                 ;; encodings
                 [org.clojure/data.json "0.2.6"]]
  :main inlet.core
  :aot :all
)
