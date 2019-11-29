#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

export logLevel=ERROR

java \
  -cp target/${JAR_FILE}:target/lib/* \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:GuaranteedSafepointInterval=300000 \
  -Dagrona.disable.bounds.checks=true \
  -Daeron.dir=/dev/shm/aeron-pong-2 \
  -Daeron.threading.mode=SHARED \
  -Daeron.archive.threading.mode=SHARED \
  -Daeron.mtu.length=8k \
  -Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8012" \
  -Daeron.archive.control.stream.id="100" \
  -Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8022" \
  -Daeron.archive.control.response.stream.id="102" \
  -Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8032" \
  -Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
  -Daeron.cluster.member.id="2" \
  -Daeron.cluster.members="0,localhost:20110,localhost:20220,localhost:20330,localhost:20440,localhost:8010|1,localhost:20111,localhost:20221,localhost:20331,localhost:20441,localhost:8011|2,localhost:20112,localhost:20222,localhost:20332,localhost:20442,localhost:8012" \
  -Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
  -Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=localhost:20552" \
  -Dio.scalecube.acpoc.instanceId=n2 \
  -Dio.scalecube.acpoc.cleanStart=true \
  -Dio.scalecube.acpoc.cleanShutdown=true \
  ${JVM_OPTS} io.scalecube.acpoc.benchmarks.ClusteredServiceRunner
