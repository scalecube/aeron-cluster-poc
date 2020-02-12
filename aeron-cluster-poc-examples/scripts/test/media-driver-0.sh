#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.dir=/dev/shm/media-driver-0 \
-Daeron.threading.mode=SHARED \
${JVM_OPTS} io.scalecube.acpoc.MediaDriverRunner
