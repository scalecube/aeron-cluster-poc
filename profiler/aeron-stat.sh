#!/bin/bash

java \
-cp profiler/samples.jar \
-Daeron.dir=$1 \
io.aeron.samples.AeronStat
