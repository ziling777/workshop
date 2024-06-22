package com.aws.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;


public class Main {

    public static void main(String[] args) throws Exception {


        ParameterTool parameter = ParameterTool.fromArgs(args);
        String kafkaBrokers = parameter.get("kafka_brokers");

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment().setParallelism(1);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBrokers)
                .setTopics("telematics")
                .setGroupId("my-group")
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStreamSource<String> kafka = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source").setParallelism(1);
        ObjectMapper objectMapper = new ObjectMapper();
        DataStream<VehicleData> dataStream = kafka.map(line -> {
            return objectMapper.readValue(line, VehicleData.class);
        }).returns(TypeInformation.of(VehicleData.class));

        dataStream.addSink(new HBaseWriterSink());

        env.execute("Kafka Sink To Hbase");
    }
}
