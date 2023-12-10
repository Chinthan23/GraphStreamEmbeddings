start_zookeeper() {
  ./producer/kafka_2.13-3.2.1/bin/zookeeper-server-start.sh ./producer/kafka_2.13-3.2.1/config/zookeeper.properties
}

start_kafka() {
  JMX_PORT=8004 ./producer/kafka_2.13-3.2.1/bin/kafka-server-start.sh producer/kafka_2.13-3.2.1/config/server.properties
}

start_cmak() {
  ./producer/CMAK/target/universal/cmak-3.0.0.7/bin/cmak -Dconfig.file=./producer/CMAK/target/universal/cmak-3.0.0.7/conf/application.conf -Dhttp.port=8080
}



if [ "START_KAFKA" = "$1" ];
then
  start_kafka
elif [ "START_ZOOKEEPER" = "$1" ];
  then
    start_zookeeper
elif [ "START_CMAK" = "$1" ];
  then
    start_cmak
else
  echo "Invalid argument"
fi