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

## Quickstart

 - Run the server on localhost.
 - Run croak on loaclhost. croak by default sends to a localhost inlet.
 - Point your browser at http://localhost:5000/hostname/iptables (but replace hostname with your computers hostname)
