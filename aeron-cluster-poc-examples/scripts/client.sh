#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.dir=/dev/shm/aeron-client-0 \
-Daeron.threading.mode=SHARED \
-Daeron.cluster.ingress.endpoints="0=localhost:20110,1=localhost:20111,2=localhost:20112" \
${JVM_OPTS} io.scalecube.acpoc.ClusterClientRunner
