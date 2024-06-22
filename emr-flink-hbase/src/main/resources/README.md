#### 创建 MSK client


##### 创建 kafka topic 并发送数据 
```shell
kafka_brokers=<msk-plaintext-bootstrap-server>
cd /home/ec2-user
sudo yum -y install java-11
wget https://archive.apache.org/dist/kafka/3.5.1/kafka_2.13-3.5.1.tgz
tar -xzf kafka_2.13-3.5.1.tgz
/home/ec2-user/kafka_2.13-3.5.1/bin/kafka-topics.sh --create --bootstrap-server ${kafka_brokers} --replication-factor 2 --partitions 1 --topic telematics

# Generate vehicle telematics data
python dtc_generator.py | /home/ec2-user/kafka_2.13-3.5.1/bin/kafka-console-producer.sh --broker-list ${kafka_brokers} --topic telematics
```

##### 另开一个 terminal 检查数据是否已经发送到 Kafka
```shell
/home/ec2-user/kafka_2.13-3.5.1/bin/kafka-console-consumer.sh --bootstrap-server ${kafka_brokers} --topic telematics --from-beginning
```