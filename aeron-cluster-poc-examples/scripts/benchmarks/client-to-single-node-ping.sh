#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

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
  -Daeron.sender.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.receiver.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Daeron.conductor.idle.strategy=org.agrona.concurrent.BusySpinIdleStrategy \
  -Dagrona.disable.bounds.checks=true \
  -Daeron.mtu.length=8k \
  -Daeron.cluster.member.endpoints="0=localhost:20110" \
  -Dio.scalecube.acpoc.messages=10000000 \
  -Dio.scalecube.acpoc.messageLength=256 \
  -Dio.scalecube.acpoc.request=1 \
  -Dio.scalecube.acpoc.cleanStart=true \
  -Dio.scalecube.acpoc.cleanShutdown=true \
  ${JVM_OPTS} io.scalecube.acpoc.benchmarks.ClusterClientPing

