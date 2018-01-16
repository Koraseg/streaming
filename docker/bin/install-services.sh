#!/usr/bin/env bash
set -ex

apt-get update && apt-get install -y wget && apt-get install -y unzip

# kafka download
rm -rf ./kafka/ || true
wget https://archive.apache.org/dist/kafka/0.11.0.1/kafka_2.11-0.11.0.1.tgz --no-check-certificate -O ./kafka.tgz
tar -xvzf ./kafka.tgz && mv ./kafka_* ./kafka/ && rm kafka.tgz

# flume download
rm -rf ./flume/ || true
wget http://apache-mirror.rbc.ru/pub/apache/flume/1.8.0/apache-flume-1.8.0-bin.tar.gz --no-check-certificate -O ./flume.tgz
tar -xvzf ./flume.tgz && mv ./apache-flume-* ./flume/ && rm flume.tgz
cp ./configs/flume* ./flume/conf/

# ignite download
rm -rf ./ignite/ || true
wget https://archive.apache.org/dist/ignite/2.1.0/apache-ignite-fabric-2.1.0-bin.zip --no-check-certificate -O ./ignite.zip
unzip ./ignite.zip && mv ./apache-ignite-* ./ignite/ && rm ignite.zip