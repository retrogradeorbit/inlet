# inlet
Receive data and RRD graph it.

## Overview

Inlet is a time-data graphing webserver. It is intended as a low-config clojure backend for croak.

It accepts serialised time-stamped data. It looks at that data to determine how regular the sampling intervals fo the data point is. As new data feeds come in, inlet automatically generates appropriate RRD databases for the storage.

Then there is a web frontend that allows a user to see graphs of the RRD data. No auth is done at this stage.

## Running

Run the server with

```
$ lein run -m inlet.core
```

Run the test suite

```
$ lein test
```

## Quickstart

 - Run the server on localhost.

   ```
   inlet$ lein run -m inlet.core
   ```

 - Run croak on loaclhost. croak by default sends to a localhost inlet.

   ```
   croak$ lein run -- -c test-config.clj
   ```

 - Point your browser at http://localhost:5000/hostname/iptables
 - Point your browser at http://localhost:5000/hostname/meminfo

   .. replace hostname with the local machines hostname.

## Configuration

Configuration is a hash map with datasource labels as the keys

```clojure
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
      }]}}
```

## License

Copyright (c) 2015 Crispin Wellington

Distributed under the Eclipse Public License version 1.0