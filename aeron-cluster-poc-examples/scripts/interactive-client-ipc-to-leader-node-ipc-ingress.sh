#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.dir=target/dev/shm/aeron-n0 \
-Dembedded.media.driver=true \
-Daeron.threading.mode=SHARED \
-Daeron.cluster.ingress.channel="aeron:ipc?term-length=64k|endpoint=ipc:0" \
-Daeron.cluster.egress.channel="aeron:udp?endpoint=localhost:10020" \
${JVM_OPTS} io.scalecube.acpoc.InteractiveClient
