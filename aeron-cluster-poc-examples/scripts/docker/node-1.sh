#!/bin/sh

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
-Dnetworkaddress.cache.ttl=0 \
-Dnetworkaddress.cache.negative.ttl=0 \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=node1:8011" \
-Daeron.archive.control.stream.id="100" \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=node1:8021" \
-Daeron.archive.control.response.stream.id="111" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=node1:8031" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Daeron.cluster.member.id="1" \
-Daeron.cluster.members="0,node0:20110,node0:20220,node0:20330,node0:20440,node0:8010
|1,node1:20111,node1:20221,node1:20331,node1:20441,node1:8011
|2,node2:20112,node2:20222,node2:20332,node2:20442,node2:8012" \
-Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
-Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=node1:20551" \
-Dio.scalecube.acpoc.instanceId=n1 \
-Dio.scalecube.acpoc.cleanStart=false \
-Dio.scalecube.acpoc.cleanShutdown=false \
-Dio.scalecube.acpoc.snapshotPeriodSecs=99999 \
  ${JVM_OPTS} io.scalecube.acpoc.ClusteredServiceRunner