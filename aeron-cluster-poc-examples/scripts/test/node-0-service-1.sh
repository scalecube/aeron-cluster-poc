#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.dir=/dev/shm/media-driver-0 \
-Daeron.archive.dir=target/archive-0 \
-Daeron.cluster.dir=target/cluster-0-1 \
-Daeron.cluster.member.id="0" \
-Daeron.cluster.members="0,localhost:20110,localhost:20220,localhost:20330,localhost:20440,localhost:8010" \
-Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
-Daeron.cluster.ingress.stream.id="1101" \
-Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=localhost:20550" \
-Daeron.cluster.log.stream.id="1102" \
-Daeron.cluster.egress.channel="aeron:udp?endpoint=localhost:9020" \
-Daeron.cluster.egress.stream.id="1103" \
-Daeron.cluster.replay.channel="aeron:ipc" \
-Daeron.cluster.replay.stream.id="1104" \
-Daeron.cluster.service.control.channel="aeron:ipc?term-length=64k|mtu=8k" \
-Daeron.cluster.service.stream.id="1105"\
-Daeron.cluster.consensus.module.stream.id="1106"\
-Daeron.cluster.snapshot.channel="aeron:ipc?alias=snapshot"\
-Daeron.cluster.snapshot.stream.id="1107"\
-Daeron.cluster.member.status.channel="aeron:udp?term-length=64k" \
-Daeron.cluster.member.status.stream.id="1108" \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8010" \
-Daeron.archive.control.stream.id="1000" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.archive.local.control.stream.id="1001" \
${JVM_OPTS} io.scalecube.acpoc.ConsensusModuleRunner


#-Daeron.archive.replication.channel="aeron:udp?endpoint=localhost:8040" \
#-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8010" \
#-Daeron.archive.control.stream.id="100" \
#-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
#-Daeron.archive.local.control.stream.id="101" \
#-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8020" \
#-Daeron.archive.control.response.stream.id="102" \
#-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8030" \
#-Daeron.archive.recording.events.stream.id="103" \
