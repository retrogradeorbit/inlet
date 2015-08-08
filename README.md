# inlet
Receive data and RRD graph it.

## Overview

Inlet is a time-data graphing webserver. It is intended as a low-config clojure backend for croak.

It accepts serialised time-stamped data. It looks at that data to determine how regular the sampling intervals fo the data point is. As new data feeds come in, inlet automatically generates appropriate RRD databases for the storage.

Then there is a web frontend that allows a user to see graphs of the RRD data. No auth is done at this stage.

## Running

Run the server with

```bash
$ lein run
```

Run the test suite

```bash
$ lein test
```

## Quickstart

 - Run the server on localhost.
 - Run croak on loaclhost. croak by default sends to a localhost inlet.
 - Point your browser at http://localhost:5000/hostname/iptables (but replace hostname with your computers hostname)

## Configuration

Configuration is a hash map with datasource labels as the keys

```clojure
{:iptables {
	    ;; data timestamp increments
	    :step 1

	    ;; graph look
	    :canvas-color [0xffffff]
	    :major-grid-color [0x00 0x00 0x00 0x20]

	    ;; define the used datapoints from the rrd
	    :defs
	    [
	     {
	     ;; what we'll call it in the graph
	     :label :input

	     ;; its rrd storage name
	     :datapoint :INPUT

	     ;; the rrd consolidation function
	     :func rrd/AVERAGE}
	     {:label :output
	      :datapoint :OUTPUT
	      :func rrd/AVERAGE}
	    ]

	    ;; define calculated graph data sets
	    :cdefs []

	    ;; the actual graph layout
	    :draw [
	     {:type :area
	      :color [0x70 0x00 0x00]
	      :label :input
	      :legend "Inbound"}
	     {:type :area
	      :color [0xd0 0x60 0x60]
	      :label :output
	      :legend "Outbound"}]}
}
```

## License

Copyright (c) 2015 Crispin Wellington

Distributed under the Eclipse Public License version 1.0