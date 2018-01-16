FROM mlaccetti/docker-oracle-java8-ubuntu-16.04

# necessary environment variables
ENV WORKDIR /streaming
ENV KAFKA_CLICKS_TOPIC clicks
ENV FLUME_SPOOL_DIR ${WORKDIR}/flumeSpool/

WORKDIR /streaming/
ADD bin/install-services.sh bin/
ADD configs/* configs/
RUN bin/install-services.sh
EXPOSE 2181 9092
ADD bin/* bin/
CMD bin/start-services.sh; sleep infinity


