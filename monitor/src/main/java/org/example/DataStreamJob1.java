package org.example;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;

import java.util.Properties;

public class DataStreamJob {

    public static void main(String[] args) throws Exception {
        // 创建 Flink 流执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 设置 Kafka 消费者属性
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "b-3.mskclustermskconnectla.rvmf3c.c7.kafka.us-east-2.amazonaws.com:9092");
        properties.setProperty("group.id", "signal-parser");

        // 创建 Kafka 消费者
        FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>(
                "ID268SystemPower",
                new SimpleStringSchema(),
                properties
        );

        // 从 Kafka 消费者获取输入数据流
        DataStream<String> inputStream = env.addSource(consumer);

        // 解析输入数据流中的信号数据
        DataStream<String> outputStream = inputStream.map(rawData -> {
            byte[] rawBytes = hexStringToByteArray(rawData);
            StringBuilder sb = new StringBuilder();
            for (SignalDefinition signal : SignalDefinition.values()) {
                double signalValue = parseSignal(rawBytes, signal);
                sb.append(signal.name()).append(": ").append((long) signalValue).append("|");
            }
            return sb.toString();
        });

        // 将解析后的数据写入 Kafka 主题
        outputStream.addSink(new FlinkKafkaProducer<>("ID268SystemPowerKV", new SimpleStringSchema(), properties));

        // 执行 Flink 作业
        env.execute("Flink Kafka Signal Parser");
    }

    // 从字节数组解析信号值
    private static double parseSignal(byte[] rawBytes, SignalDefinition signal) {
        int startBit = signal.getStartBit();
        int length = signal.getLength();
        boolean isSigned = signal.isSigned();
        double minimum = signal.getMinimum();
        double maximum = signal.getMaximum();
        double scale = signal.getScale();

        int startByte = startBit / 8;
        int endByte = (startBit + length - 1) / 8;
        int bitOffset = startBit % 8;

        long signalValue = 0;
        for (int i = endByte; i >= startByte; i--) {
            int shift = (endByte - i) * 8;
            signalValue |= ((long) (rawBytes[i] & 0xFF)) << shift;
        }

        signalValue = (signalValue >>> bitOffset) & ((1L << length) - 1);
        if (isSigned && (signalValue >> (length - 1)) != 0) {
            signalValue |= (-1L) << length;
        }

        double scaledValue = signalValue * scale;
        return Math.round(Math.max(Math.min(scaledValue, maximum), minimum));
    }

    // 将十六进制字符串转换为字节数组
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    // 信号定义枚举
    private enum SignalDefinition {
        SystemRegenPowerMax268(32, 8, true, -100, 155, 1),
        SystemDrivePowerMax268(16, 9, false, 0, 511, 1),
        DI_primaryUnitSiliconType(26, 1, false, 0, 1, 1),
        SystemHeatPowerMax268(0, 8, false, 0, 31875, 0.08),
        SystemHeatPower268(8, 8, false, 0, 31875, 0.08);

        private final int startBit;
        private final int length;
        private final boolean isSigned;
        private final double minimum;
        private final double maximum;
        private final double scale;

        SignalDefinition(int startBit, int length, boolean isSigned, double minimum, double maximum, double scale) {
            this.startBit = startBit;
            this.length = length;
            this.isSigned = isSigned;
            this.minimum = minimum;
            this.maximum = maximum;
            this.scale = scale;
        }

        public int getStartBit() {
            return startBit;
        }

        public int getLength() {
            return length;
        }

        public boolean isSigned() {
            return isSigned;
        }

        public double getMinimum() {
            return minimum;
        }

        public double getMaximum() {
            return maximum;
        }

        public double getScale() {
            return scale;
        }
    }
}
