#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=mpp-habryiel:8011" \
-Daeron.archive.control.stream.id="100" \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=mpp-habryiel:8021" \
-Daeron.archive.control.response.stream.id="101" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=mpp-habryiel:8031" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.cluster.member.id="1" \
-Daeron.cluster.members="0,mpp-vysochyn:20110,mpp-vysochyn:20220,mpp-vysochyn:20330,mpp-vysochyn:20440,mpp-vysochyn:8010|1,mpp-habryiel:20111,mpp-habryiel:20221,mpp-habryiel:20331,mpp-habryiel:20441,mpp-habryiel:8011
-Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
-Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=mpp-habryiel:20551" \
-Dio.scalecube.acpoc.instanceId=n1 \
-Dio.scalecube.acpoc.cleanStart=false \
-Dio.scalecube.acpoc.cleanShutdown=false \
-Dio.scalecube.acpoc.snapshotPeriodSecs=99999 \
-Daeron.cluster.session.timeout=30000000000 \
-Daeron.cluster.leader.heartbeat.timeout=2000000000 \
  ${JVM_OPTS} io.scalecube.acpoc.ClusteredServiceRunner