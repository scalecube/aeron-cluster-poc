#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
  -Dcluster.node.instanceId="1" \
  -Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8011" \
  -Daeron.archive.control.stream.id="100" \
  -Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8021" \
  -Daeron.archive.control.response.stream.id="101" \
  -Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8031" \
  -Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
  -Daeron.cluster.member.id="1" \
  -Daeron.cluster.members="1,localhost:2011,localhost:2022,localhost:2033,localhost:2044,localhost:8011" \
  -Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
  -Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=localhost:20551" \
  ${JVM_OPTS} io.scalecube.acpoc.ClusterJoinTest
