#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8014" \
-Daeron.archive.control.stream.id=100 \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8024" \
-Daeron.archive.control.response.stream.id=100 \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8034" \
-Daeron.cluster.member.id=4 \
-Daeron.cluster.member.status.channel="aeron:udp?term-length=64k|endpoint=20224" \
-Daeron.cluster.members="4,localhost:20114,localhost:20224,localhost:20334,localhost:20444,localhost:8014" \
-Daeron.cluster.members.status.endpoints="localhost:20220,localhost:20221,localhost:20222" \
-Dio.scalecube.acpoc.instanceId=n4 \
-Dio.scalecube.acpoc.cleanStart=false \
-Dio.scalecube.acpoc.cleanShutdown=false \
  ${JVM_OPTS} io.scalecube.acpoc.ClusteredServiceRunner
