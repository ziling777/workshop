package com.aws.analytics;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;

import java.util.stream.Collectors;

public class HBaseWriterSink extends RichSinkFunction<VehicleData> {
    Connection hbase_conn;
    Table tb;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        org.apache.hadoop.conf.Configuration hbaseConf = HBaseConfiguration.create();
        hbaseConf.addResource("/etc/hbase/conf/hbase-site.xml");
        hbase_conn = ConnectionFactory.createConnection(hbaseConf);
        tb = hbase_conn.getTable(TableName.valueOf("vehicle_info"));
    }


    // {"vehicle_id": "74528", "timestamp": "2024-06-21T06:20:07.014727Z", "location": {"latitude": 75.970324, "longitude": -115.307112}, "speed": 71.07, "fuel_consumption": 11.81, "engine_temperature": 73.03, "tire_pressure": {"front_left": 34.61, "front_right": 32.84, "rear_left": 32.73, "rear_right": 32.39}, "battery_voltage": 13.82, "odometer": 152818.4, "engine_rpm": 585.4, "coolant_temperature": 73.5, "throttle_position": 17.05, "gear_position": "D", "vehicle_brand": "Audi", "vehicle_model": "A3", "manufacture_year": 2014, "owner_info": {"name": "Robert Brown", "contact": "555-123-4567"}, "dtc_codes": [{"code": "P0500", "description": "Vehicle Speed Sensor Malfunction"}, {"code": "P2101", "description": "Throttle Actuator Control Motor Circuit Range/Performance"}, {"code": "P0750", "description": "Shift Solenoid 'A' Malfunction"}, {"code": "P1000", "description": "OBD Systems Readiness Test Not Complete"}], "battery_level": null, "charging_status": null}
    @Override
    public void invoke(VehicleData value, Context context) throws Exception {
        byte[] rowKey = Bytes.toBytes(value.getVehicle_id());
        Put put = new Put(rowKey);

        // Add vehicle data to the put
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("location_latitude"), Bytes.toBytes(Double.toString(value.getLocation().getLatitude())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("location_longitude"), Bytes.toBytes(Double.toString(value.getLocation().getLongitude())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("speed"), Bytes.toBytes(Double.toString(value.getSpeed())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("fuel_consumption"), Bytes.toBytes(Double.toString(value.getFuel_consumption())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("engine_temperature"), Bytes.toBytes(Double.toString(value.getEngine_temperature())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("tire_pressure_front_left"), Bytes.toBytes(Double.toString(value.getTire_pressure().getFront_left())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("tire_pressure_front_right"), Bytes.toBytes(Double.toString(value.getTire_pressure().getFront_right())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("tire_pressure_rear_left"), Bytes.toBytes(Double.toString(value.getTire_pressure().getRear_left())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("tire_pressure_rear_right"), Bytes.toBytes(Double.toString(value.getTire_pressure().getRear_right())));

        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("battery_voltage"), Bytes.toBytes(Double.toString(value.getBattery_voltage())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("odometer"), Bytes.toBytes(Double.toString(value.getOdometer())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("engine_temperature"), Bytes.toBytes(Double.toString(value.getEngine_temperature())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("throttle_position"), Bytes.toBytes(Double.toString(value.getThrottle_position())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("gear_position"), Bytes.toBytes(value.getGear_position()));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("vehicle_brand"), Bytes.toBytes(value.getVehicle_brand()));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("vehicle_model"), Bytes.toBytes(value.getVehicle_model()));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("manufacture_year"), Bytes.toBytes(value.getVehicle_model()));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("owner_info_name"), Bytes.toBytes(value.getOwner_info().getName()));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("owner_info_contact"), Bytes.toBytes(value.getOwner_info().getContact()));

        String dtcCodes = value.getDtc_codes().stream()
                .map(dtcCode -> dtcCode.getCode() + ":" + dtcCode.getDescription())
                .collect(Collectors.joining(","));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("dtc_codes"), Bytes.toBytes(dtcCodes));

        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("battery_level"), Bytes.toBytes(Double.toString(value.getBattery_level())));
        put.addColumn(Bytes.toBytes("vehicle_info"), Bytes.toBytes("charging_status"), Bytes.toBytes(value.getCharging_status()));
        tb.put(put);
    }

    public void close() throws Exception {
        if (null != tb) tb.close();
        if (null != hbase_conn) hbase_conn.close();
    }
}