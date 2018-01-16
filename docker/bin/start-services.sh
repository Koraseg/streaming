#!/usr/bin/env bash
echo "Starting the services!"
topic=${KAFKA_CLICKS_TOPIC:-clicks}
echo ${topic}
mkdir ${FLUME_SPOOL_DIR}

cd ./kafka/

exec ./bin/zookeeper-server-start.sh ./config/zookeeper.properties &
echo "Zookeeper started"
sleep 10

exec ./bin/kafka-server-start.sh ./config/server.properties &
echo "Kafka server started"
sleep 10

echo "Creating a topic ${topic}"
exec ./bin/kafka-topics.sh --create --topic ${topic} --partitions 3 --replication-factor 1 --zookeeper 127.0.0.1:2181 &

echo "Starting a flume agent"
cd ../flume/

exec ./bin/flume-ng agent  --conf conf/ -f ./conf/flume-agent.conf -n a1 -Dflume.root.logger=INFO,console -DpropertiesImplementation=org.apache.flume.node.EnvVarResolverProperties &

echo "Starting an ignite node"
cd ../ignite/
exec ./bin/ignite.sh &
