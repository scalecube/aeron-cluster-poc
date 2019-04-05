#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
    -cp target/${JAR_FILE}:target/lib/* \
    -Daeron.archive.control.channel="aeron:udp?endpoint=localhost:8010" \
    -Daeron.archive.control.response.channel="aeron:udp?endpoint=localhost:8020" \
    -Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8030" \
    -Daeron.cluster.member.endpoints="localhost:10000,localhost:20000,localhost:30000,localhost:40000,localhost:8010" \
    -Daeron.cluster.members.status.endpoints="localhost:20001" \
    ${JVM_OPTS} io.scalecube.acpoc.ClusterJoinTest
