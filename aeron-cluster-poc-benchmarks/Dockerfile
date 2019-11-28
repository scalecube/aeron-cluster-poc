FROM openjdk:8

ARG SERVICE_NAME
ARG EXECUTABLE_JAR
ENV SERVICE_NAME ${SERVICE_NAME}

WORKDIR /opt/benchmarks

ENV DEFAULT_JAVA_OPTS="-server \
-XX:+DisableExplicitGC \
-Dsun.rmi.dgc.client.gcInterval=3600000 \
-Dsun.rmi.dgc.server.gcInterval=3600000"

ENV DEFAULT_OOM_OPTS="-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=$SERVICE_NAME-oom.hprof \
-XX:+UseGCOverheadLimit"

COPY target/lib lib
COPY target/${EXECUTABLE_JAR}.jar app.jar

CMD exec java \
$DEFAULT_JAVA_OPTS \
$JAVA_OPTS \
$DEFAULT_OOM_OPTS \
-cp app.jar $PROGRAM_ARGS
