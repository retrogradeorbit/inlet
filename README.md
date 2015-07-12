# inlet
Receive data and RRD graph it.

## Overview

This is intended as a quick, low config backend for croak. Who knows
what it will become.

It accepts and serialised time stamp data and does its best to work out if it's new or not, and where it belongs and how it should be processed. As new data feeds come in, inlet automatically generates appropriate RRD databases for the storage.

Then, at regular intervals, it rebuilds the RRD graph images. These graphs can be viewed through a simple HTTP interface. No auth is done at this stage.

## Running

Run the server with

```bash
$ lein run
```
