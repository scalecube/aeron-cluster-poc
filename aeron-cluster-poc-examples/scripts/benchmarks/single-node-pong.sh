#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

export logLevel=ERROR

java \
  -cp target/${JAR_FILE}:target/lib/* \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+TrustFinalNonStaticFields \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:GuaranteedSafepointInterval=300000 \
  -XX:BiasedLockingStartupDelay=0 \
  -XX:+UseParallelOldGC \
  -Daeron.term.buffer.sparse.file=false \
  -Daeron.socket.so_sndbuf=2m \
  -Daeron.socket.so_rcvbuf=2m \
  -Daeron.rcv.initial.window.length=2m \
  -Daeron.threading.mode=DEDICATED \
  -Daeron.archive.threading.mode=DEDICATED \
  -Daeron.shared.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.sharednetwork.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.archive.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.archive.recorder.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.archive.replayer.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.archive.file.sync.level=0 \
  -Daeron.archive.segment.file.length=1g \
  -Daeron.archive.control.mtu.length=4k \
  -Daeron.spies.simulate.connection=true \
  -Dagrona.disable.bounds.checks=true \
  -Daeron.mtu.length=8k \
  -Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8010" \
  -Daeron.archive.control.stream.id="100" \
  -Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8020" \
  -Daeron.archive.control.response.stream.id="100" \
  -Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8030" \
  -Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
  -Daeron.cluster.member.id="0" \
  -Daeron.cluster.members="0,localhost:20110,localhost:20220,localhost:20330,localhost:20440,localhost:8010" \
  -Daeron.cluster.ingress.channel="aeron:udp?term-length=64k" \
  -Daeron.cluster.log.channel="aeron:udp?term-length=256k|control-mode=manual|control=localhost:20550" \
  -Dio.scalecube.acpoc.instanceId=n0 \
  -Dio.scalecube.acpoc.cleanStart=true \
  -Dio.scalecube.acpoc.cleanShutdown=true \
  ${JVM_OPTS} io.scalecube.acpoc.benchmarks.ClusteredServiceRunner
