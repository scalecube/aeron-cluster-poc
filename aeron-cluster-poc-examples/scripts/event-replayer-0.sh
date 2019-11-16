#!/usr/bin/env bash

cd $(dirname $0)
cd ../

JAR_FILE=$(ls target |grep jar)

echo $JAR_FILE

java \
  -cp target/${JAR_FILE}:target/lib/* \
-Daeron.archive.control.channel="aeron:udp?term-length=64k|endpoint=localhost:8014" \
-Daeron.archive.control.stream.id="100" \
-Daeron.archive.control.response.channel="aeron:udp?term-length=64k|endpoint=localhost:8025" \
-Daeron.archive.control.response.stream.id="105" \
-Daeron.archive.recording.events.channel="aeron:udp?control-mode=dynamic|control=localhost:8034" \
-Daeron.archive.local.control.channel="aeron:ipc?term-length=64k" \
-Dposition=-1 \
-DrecordingId=0 \
  ${JVM_OPTS} io.scalecube.acpoc.EventReplayer




#RecordingDescriptor{controlSessionId=875439959, correlationId=6, recordingId=0, startTimestamp=1573939321893, stopTimestamp=-1, startPosition=0, stopPosition=-1, initialTermId=-1209439124, segmentFileLength=67108864, termBufferLength=67108864, mtuLength=1408, sessionId=-2110003911, streamId=12123, strippedChannel='aeron:ipc?session-id=-80879683', originalChannel='aeron:ipc?session-id=-80879683', sourceIdentity='aeron:ipc?session-id=-80879683', recordingPosition=0}
#RecordingDescriptor{controlSessionId=1033978114, correlationId=6, recordingId=2, startTimestamp=1573941107103, stopTimestamp=-1, startPosition=128, stopPosition=-1, initialTermId=-1209439124, segmentFileLength=134217728, termBufferLength=67108864, mtuLength=1408, sessionId=-1691463487, streamId=12123, strippedChannel='aeron:ipc?session-id=-1691463487', originalChannel='aeron:ipc?init-term-id=-1209439124|mtu=1408|term-id=-1209439124|term-length=67108864|term-offset=128|session-id=-1691463487', sourceIdentity='aeron:ipc?session-id=-1691463487', recordingPosition=0}