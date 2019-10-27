#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:GuaranteedSafepointInterval=300000 \
  -Daeron.mtu.length=16k \
  -Daeron.threading.mode=DEDICATED \
  -Dagrona.disable.bounds.checks=true \
  -Daeron.cluster.member.endpoints="0=localhost:20110" \
  -Dio.scalecube.acpoc.messages=10000000 \
  -Dio.scalecube.acpoc.messageLength=256 \
  -Dio.scalecube.acpoc.request=1 \
  -Dio.scalecube.acpoc.cleanStart=true \
  -Dio.scalecube.acpoc.cleanShutdown=true \
  ${JVM_OPTS} io.scalecube.acpoc.benchmarks.ClusterClientPing

