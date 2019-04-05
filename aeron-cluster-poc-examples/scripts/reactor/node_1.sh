#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
    -cp target/${JAR_FILE}:target/lib/* \
    -Daeron.archive.control.channel="aeron:udp?endpoint=localhost:7010" \
    -Daeron.archive.control.response.channel="aeron:udp?endpoint=localhost:7020" \
    -Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:7030" \
    -Daeron.cluster.members="0,localhost:10001,localhost:20001,localhost:30001,localhost:40001,localhost:7010" \
    ${JVM_OPTS} io.scalecube.acpoc.ClusterJoinTest
