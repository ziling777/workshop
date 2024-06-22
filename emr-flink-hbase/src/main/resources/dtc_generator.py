import json
import time
import socket
from datetime import datetime
import random

dtc_codes_list = [
    {"code": "P0301", "description": "Cylinder 1 Misfire Detected"},
    {"code": "P0420", "description": "Catalyst System Efficiency Below Threshold (Bank 1)"},
    {"code": "P0171", "description": "System Too Lean (Bank 1)"},
    {"code": "P0172", "description": "System Too Rich (Bank 1)"},
    {"code": "P0455", "description": "Evaporative Emission Control System Leak Detected"},
    {"code": "P0401", "description": "Exhaust Gas Recirculation Flow Insufficient Detected"},
    {"code": "P0128", "description": "Coolant Thermostat Temperature Below Regulating Temperature"},
    {"code": "P0442", "description": "Evaporative Emission Control System Leak Detected (small leak)"},
    {"code": "P0500", "description": "Vehicle Speed Sensor Malfunction"},
    {"code": "P0705", "description": "Transmission Range Sensor Circuit malfunction (PRNDL Input)"},
    {"code": "P0102", "description": "Mass or Volume Air Flow Circuit Low Input"},
    {"code": "P0113", "description": "Intake Air Temperature Sensor 1 Circuit High Input"},
    {"code": "P0191", "description": "Fuel Rail Pressure Sensor Circuit Range/Performance"},
    {"code": "P0300", "description": "Random/Multiple Cylinder Misfire Detected"},
    {"code": "P0562", "description": "System Voltage Low"},
    {"code": "P0606", "description": "ECM/PCM Processor"},
    {"code": "P0700", "description": "Transmission Control System (MIL Request)"},
    {"code": "P2101", "description": "Throttle Actuator Control Motor Circuit Range/Performance"},
    {"code": "P0740", "description": "Torque Converter Clutch Circuit Malfunction"},
    {"code": "P0750", "description": "Shift Solenoid 'A' Malfunction"},
    {"code": "P0760", "description": "Shift Solenoid 'C' Malfunction"},
    {"code": "P0770", "description": "Shift Solenoid 'E' Malfunction"},
    {"code": "P0800", "description": "Transfer Case Control System (MIL Request)"},
    {"code": "P0900", "description": "Clutch Actuator Circuit/Open"},
    {"code": "P1000", "description": "OBD Systems Readiness Test Not Complete"}
]

owner_names = ["John Doe", "Jane Smith", "Alice Johnson", "Robert Brown", "Michael Davis"]
owner_contacts = ["123-456-7890", "098-765-4321", "555-123-4567", "666-777-8888", "999-000-1111"]
vins = ["1HGBH41JXMN109186", "1HGBH41JXMN109187", "1HGBH41JXMN109188", "1HGBH41JXMN109189", "1HGBH41JXMN109190"]
brands = ["Toyota", "Hyundai", "Tesla", "NIO", "BYD"]
models = ["Corolla", "Sonata", "Model 3", "ES8", "Tang"]
manufacture_years = ["2018", "2019", "2020", "2021", "2022"]

def generate_random_vehicle_data():
    vehicle_data_list = []
    for i in range(len(vins)):
        owner_name = owner_names[i]
        owner_contact = owner_contacts[i]
        vin = vins[i]
        brand = brands[i]
        model = models[i]
        manufacture_year = manufacture_years[i]
        is_electric = brand in ["Tesla", "NIO", "BYD"]

        vehicle_data = {
            "vehicle_id": vin,
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "location": {
                "latitude": round(random.uniform(-90.0, 90.0), 6),
                "longitude": round(random.uniform(-180.0, 180.0), 6)
            },
            "speed": round(random.uniform(0, 200), 2),
            "fuel_consumption": 0 if is_electric else round(random.uniform(5, 20), 2),
            "engine_temperature": 0 if is_electric else round(random.uniform(70, 120), 2),
            "tire_pressure": {
                "front_left": round(random.uniform(30, 35), 2),
                "front_right": round(random.uniform(30, 35), 2),
                "rear_left": round(random.uniform(30, 35), 2),
                "rear_right": round(random.uniform(30, 35), 2)
            },
            "battery_voltage": round(random.uniform(12, 14.5), 2) if is_electric else 0,
            "odometer": round(random.uniform(0, 300000), 1),
            "engine_rpm": 0 if is_electric else round(random.uniform(500, 7000), 1),
            "coolant_temperature": 0 if is_electric else round(random.uniform(70, 120), 2),
            "throttle_position": round(random.uniform(0, 100), 2),
            "gear_position": random.choice(["P", "R", "N", "D"]),
            "vehicle_brand": brand,
            "vehicle_model": model,
            "manufacture_year": manufacture_year,
            "owner_info": {
                "name": owner_name,
                "contact": owner_contact
            },
            "dtc_codes": random.sample(dtc_codes_list, random.randint(1, 5)),
            "battery_level": round(random.uniform(0, 100), 2) if is_electric else 0,
            "charging_status": random.choice(["Charging", "Not Charging"]) if is_electric else "N/A"
        }
        vehicle_data_list.append(vehicle_data)
    return vehicle_data_list



def main():
    while True:
        vehicle_data_list = generate_random_vehicle_data()
        for vehicle_data in vehicle_data_list:
            print(json.dumps(vehicle_data))
        time.sleep(5)


if __name__ == "__main__":
    main()
