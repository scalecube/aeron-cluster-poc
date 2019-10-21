#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
-cp target/${JAR_FILE}:target/lib/* \
-Daeron.cluster.member.endpoints="0=mpp-vysochyn:20110,1=mpp-habryiel:20111,2=om2-savchuk:20112" \
-Dnetworkaddress.cache.ttl=0 \
-Dnetworkaddress.cache.negative.ttl=0 \
${JVM_OPTS} io.scalecube.acpoc.InteractiveClient