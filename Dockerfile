FROM openjdk:8

RUN apt-get update
RUN apt-get -y install vim

COPY aeron-cluster-poc-examples/target /app/target
COPY aeron-cluster-poc-examples/scripts/docker /app/scripts

WORKDIR /app

ENV NUMBER="0"

RUN ["chmod", "+x", "scripts/node-0.sh"]
RUN ["chmod", "+x", "scripts/node-1.sh"]
RUN ["chmod", "+x", "scripts/node-2.sh"]
CMD ["/bin/sh", "-c", "scripts/node-${NUMBER}.sh"]