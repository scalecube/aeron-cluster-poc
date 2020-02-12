#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.dir=/dev/shm/media-driver-0 \
-Daeron.archive.dir=target/archive-0 \
-Daeron.archive.threading.mode=SHARED \
-Daeron.archive.replication.channel="aeron:udp?endpoint=localhost:8040" \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8010" \
-Daeron.archive.control.stream.id="1000" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.archive.local.control.stream.id="1001" \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8020" \
-Daeron.archive.control.response.stream.id="1002" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8030" \
-Daeron.archive.recording.events.stream.id="1003" \
-Daeron.archive.recording.events.enabled="true" \
${JVM_OPTS} io.scalecube.acpoc.ArchiveRunner
