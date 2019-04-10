#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:GuaranteedSafepointInterval=300000 \
  -Daeron.threading.mode=SHARED \
  -Dagrona.disable.bounds.checks=true \
  -Daeron.mtu.length=16k \
  -Daeron.cluster.member.endpoints="0=localhost:20110,1=localhost:20111,2=localhost:20112" \
  -Dio.scalecube.acpoc.messageLength=256 \
  -Dio.scalecube.acpoc.request=128 \
  ${JVM_OPTS} io.scalecube.acpoc.ClusterClientBenchmark

