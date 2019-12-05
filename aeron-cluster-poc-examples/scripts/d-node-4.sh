#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.dir=/dev/shm/aeron-d4 \
-Daeron.threading.mode=SHARED \
-Daeron.archive.threading.mode=SHARED \
-Daeron.cluster.member.id="-1" \
-Daeron.cluster.members="" \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8014" \
-Daeron.archive.control.stream.id="100" \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8024" \
-Daeron.archive.control.response.stream.id="114" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8034" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.cluster.member.endpoints="localhost:20114,localhost:20224,localhost:20334,localhost:20444,localhost:8014" \
-Daeron.cluster.members.status.endpoints="localhost:20220" \
-Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
-Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=localhost:20554" \
-Dio.scalecube.acpoc.cleanStart=true \
-Dio.scalecube.acpoc.cleanShutdown=true \
${JVM_OPTS} io.scalecube.acpoc.ClusteredServiceRunner
