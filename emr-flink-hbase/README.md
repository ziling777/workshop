### EMR Kafka + Flink + HBase Example
* EMR version：6.5.0 (flink-1.14.0 hbase 2.4.4)
* 编译
```shell
mvn clean package -Dscope.type=provided
```
* 在 EMR Master 节点提交作业
```shell
# 修改 /etc/flink/conf/flink-conf.yaml, 添加：
classloader.resolve-order: parent-first
```
```shell
# 创建hbase table
create 'vehicle_info', 'vehicle_info'
```
```shell
# 提交 Flink 作业, 
kafka_brokers=<msk-plaintext-bootstrap-server>
flink run-application -t yarn-application ./emr-flink-hbase-1.0.jar --kafka_brokers ${kafka_brokers}
```


