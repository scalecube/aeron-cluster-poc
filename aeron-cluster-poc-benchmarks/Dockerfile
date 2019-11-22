FROM openjdk:8

ARG SERVICE_NAME
ARG EXECUTABLE_JAR
ENV DUMPS_DIR /opt/exchange/dumps
ENV SERVICE_NAME ${SERVICE_NAME}

WORKDIR /opt/exchange

RUN mkdir -p $DUMPS_DIR

ENV DEFAULT_JAVA_OPTS="-server \
-XX:+DisableExplicitGC \
-Dsun.rmi.dgc.client.gcInterval=3600000 \
-Dsun.rmi.dgc.server.gcInterval=3600000"

ENV DEFAULT_OOM_OPTS="-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=$DUMPS_DIR/$SERVICE_NAME-oom.hprof \
-XX:+UseGCOverheadLimit"

COPY target/lib lib
COPY target/${EXECUTABLE_JAR}.jar benchmarks.jar

# jmx server port
EXPOSE 5678

CMD exec java \
$DEFAULT_JAVA_OPTS \
$JAVA_OPTS \
$DEFAULT_OOM_OPTS \
$OOM_OPTS \
-cp benchmarks.jar $PROGRAM_ARGS
