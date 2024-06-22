package com.aws.analytics;

import lombok.Data;

import java.util.List;

@Data
public class VehicleData {
    private String vehicle_id;
    private String timestamp;
    private Location location;
    private double speed;
    private double fuel_consumption;
    private double engine_temperature;
    private TirePressure tire_pressure;
    private double battery_voltage;
    private double odometer;
    private double engine_rpm;
    private double coolant_temperature;
    private double throttle_position;
    private String gear_position;
    private String vehicle_brand;
    private String vehicle_model;
    private int manufacture_year;
    private OwnerInfo owner_info;
    private List<DtcCode> dtc_codes;
    private double battery_level;
    private String charging_status;

    // getters and setters
}

@Data
class Location {
    private double latitude;
    private double longitude;

    // getters and setters
}

@Data
class TirePressure {
    private double front_left;
    private double front_right;
    private double rear_left;
    private double rear_right;

    // getters and setters
}

@Data
class OwnerInfo {
    private String name;
    private String contact;

    // getters and setters
}

@Data
class DtcCode {
    private String code;
    private String description;

    // getters and setters
}