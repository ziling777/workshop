package org.example;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Preconditions;
import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime;
import java.util.Map;
import java.util.Properties;


import java.util.Properties;

public class DataStreamJob {
    
    public static final String MSKBOOTSTRAP_SERVERS_KEY = "MSKBootstrapServers";
    
    public static void main(String[] args) throws Exception {
        // 创建 Flink 流执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Get application properties
        Map<String, Properties> applicationProperties = KinesisAnalyticsRuntime.getApplicationProperties();
        System.out.println("applicationProperties size: " + applicationProperties.size());
        
        System.out.println("\nApplicationProperties:");
        for (Map.Entry<String, Properties> entry : applicationProperties.entrySet()) {
            String key = entry.getKey();
            Properties props = entry.getValue();
            System.out.println("- " + key + ":");
            props.list(System.out);
            System.out.println();
        }
        
        Properties consumerConfigProperties = applicationProperties.get("flinkapp");
        
    
        // 从应用程序属性中获取 MSK Bootstrap Servers 地址
        String bootstrapServers = null;
        if (consumerConfigProperties != null) {
            bootstrapServers = consumerConfigProperties.getProperty(MSKBOOTSTRAP_SERVERS_KEY);
        }
        
    
        // 检查 MSK Bootstrap Servers 地址是否存在
        Preconditions.checkNotNull(bootstrapServers, MSKBOOTSTRAP_SERVERS_KEY + " configuration missing");

 
         // 设置 Kafka 消费者属性
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", bootstrapServers);
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
                sb.append(parseSignal(rawBytes, signal)).append("|");
            }
            return sb.toString();
        });

        // 将解析后的数据写入 Kafka 主题
        outputStream.addSink(new FlinkKafkaProducer<>("ID268SystemPowerKV", new SimpleStringSchema(), properties));

        // 执行 Flink 作业
        env.execute("Flink Kafka Signal Parser");
    }

    // 从字节数组解析信号值
    private static String parseSignal(byte[] rawBytes, SignalDefinition signal) {
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

        long signedSignalValue;
        if (isSigned) {
            int signBitIndex = length - 1;
            long signBit = (signalValue >> signBitIndex) & 1;
            long signExtension = (signBit == 0) ? 0 : ~((1L << signBitIndex) - 1);
            signedSignalValue = (signalValue & ((1L << signBitIndex) - 1)) | (signExtension << signBitIndex);
            signedSignalValue = (signedSignalValue >> bitOffset);
        } else {
            signedSignalValue = (signalValue >> bitOffset) & ((1L << length) - 1);
        }

        double scaledValue = signedSignalValue * scale;
        double clampedValue = Math.max(Math.min(scaledValue, maximum), minimum);
        boolean isValueClamped = clampedValue != scaledValue;

        return signal.name() + ": " + (long) clampedValue + (isValueClamped ? " (clamped)" : "");
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
        SystemRegenPowerMax268(32, 8, true, -128, 127, 1),
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
