#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8010" \
-Daeron.archive.control.stream.id="100" \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8020" \
-Daeron.archive.control.response.stream.id="100" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8030" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.cluster.member.id="0" \
-Daeron.cluster.members="0,localhost:20110,localhost:20220,localhost:20330,localhost:20440,localhost:8010|1,localhost:20111,localhost:20221,localhost:20331,localhost:20441,localhost:8011|2,localhost:20112,localhost:20222,localhost:20332,localhost:20442,localhost:8012" \
-Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
-Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=localhost:20550" \
  ${JVM_OPTS} io.scalecube.acpoc.ClusterServiceRunner