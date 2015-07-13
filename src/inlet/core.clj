(ns inlet.core
  (:require [compojure.core :refer :all]
            [org.httpkit.server :refer [run-server]])
)

(defn data [req]
  (println req)
  "OK"
  )

(defroutes myapp
  (GET "/" [] "Hello World")
  (POST "/data" req (data req) )
  )

(defn -main []
  (run-server myapp {:port 5000}))
