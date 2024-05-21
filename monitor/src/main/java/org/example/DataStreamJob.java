/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.example;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import com.amazonaws.services.sns.model.PublishRequest;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DataStreamJob {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

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

        AmazonSNSAsync snsClient = SNSClientProvider.getSNSClient();
        String snsTopicArn = "arn:aws:sns:us-east-2:082526546443:vehiclemonitor";

        env.addSource(consumer)
                .map(DataStreamJob::parseVehiclePower)
                .keyBy(VehiclePower::getVehicleId)
                .process(new BatteryHeatMonitor(10000, 0.9))
                .addSink(new SNSAlertSink(snsTopicArn))
                .setParallelism(1);

        env.execute("Vehicle Power Monitor");
    }

    /**
     * 解析消息字符串，返回 VehiclePower 对象
     *
     * 假设消息字符串的格式为 "key1: value1|key2: value2|..."
     * 例如: "SystemRegenPowerMax268: 100|SystemDrivePowerMax268: 256|DI_primaryUnitSiliconType: 0|SystemHeatPowerMax268: 0|SystemHeatPower268: 0|"
     */
    private static VehiclePower parseVehiclePower(String msg) {
        Map<String, Double> fields = new HashMap<>();
        for (String pair : msg.split("\\|")) {
            String[] kv = pair.split(": ");
            if (kv.length == 2) {
                fields.put(kv[0], Double.parseDouble(kv[1]));
            }
        }

        String vehicleId = "..."; // 从某个字段或其他地方获取车辆ID
        double systemHeatPower268 = fields.getOrDefault("SystemHeatPower268", 0.0);
        double systemHeatPowerMax268 = fields.getOrDefault("SystemHeatPowerMax268", 0.0);

        return new VehiclePower(vehicleId, systemHeatPower268, systemHeatPowerMax268);
    }

    private static class VehiclePower {
        private String vehicleId;
        private double systemHeatPower268;
        private double systemHeatPowerMax268;

        public VehiclePower(String vehicleId, double systemHeatPower268, double systemHeatPowerMax268) {
            this.vehicleId = vehicleId;
            this.systemHeatPower268 = systemHeatPower268;
            this.systemHeatPowerMax268 = systemHeatPowerMax268;
        }

        public String getVehicleId() {
            return vehicleId;
        }

        public double getSystemHeatPower268() {
            return systemHeatPower268;
        }

        public double getSystemHeatPowerMax268() {
            return systemHeatPowerMax268;
        }
    }

    private static class BatteryHeatMonitor extends ProcessFunction<VehiclePower, AlertMessage> {
        private final double maxHeatPower;
        private final double saturateRatio;

        public BatteryHeatMonitor(double maxHeatPower, double saturateRatio) {
            this.maxHeatPower = maxHeatPower;
            this.saturateRatio = saturateRatio;
        }

        @Override
        public void processElement(VehiclePower value, Context ctx, Collector<AlertMessage> out) throws Exception {
            double heatPower = value.getSystemHeatPower268();
            double maxHeatPower = value.getSystemHeatPowerMax268();

            if (heatPower > maxHeatPower) {
                out.collect(new AlertMessage(String.format("Vehicle %s battery heat power %.2f exceeds maximum %.2f", value.getVehicleId(), heatPower, maxHeatPower)));
            } else if (heatPower > saturateRatio * maxHeatPower) {
                out.collect(new AlertMessage(String.format("Vehicle %s battery heat management is about to saturate: %.2f / %.2f", value.getVehicleId(), heatPower, maxHeatPower)));
            }
        }
    }

    private static class AlertMessage {
        private String message;

        public AlertMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class SNSAlertSink implements SinkFunction<AlertMessage> {
        private final String snsTopicArn;

        public SNSAlertSink(String snsTopicArn) {
            this.snsTopicArn = snsTopicArn;
        }

        @Override
        public void invoke(AlertMessage alert, Context context) throws Exception {
            AmazonSNSAsync snsClient = SNSClientProvider.getSNSClient();
            PublishRequest request = new PublishRequest(snsTopicArn, alert.getMessage());
            snsClient.publishAsync(request);
        }
    }
}

class SNSClientProvider {
    private static final AmazonSNSAsync snsClient = AmazonSNSAsyncClientBuilder.defaultClient();

    public static AmazonSNSAsync getSNSClient() {
        return snsClient;
    }
}
