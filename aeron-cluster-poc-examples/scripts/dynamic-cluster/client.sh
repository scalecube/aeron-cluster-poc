#!/usr/bin/env bash

cd $(dirname $0)
cd ../../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
  -Daeron.cluster.member.endpoints="0=localhost:2011,1=localhost:2111,2=localhost:2211" \
  ${JVM_OPTS} io.scalecube.acpoc.ClientTest

