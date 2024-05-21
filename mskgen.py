import cantools
import random
import time
from kafka import KafkaProducer

# Kafka配置
bootstrap_servers = ['b-3.mskclustermskconnectla.rvmf3c.c7.kafka.us-east-2.amazonaws.com:9092']
topic_name = 'ID268SystemPower'

# 创建Kafka生产者,配置明文传输
producer = KafkaProducer(
    bootstrap_servers=bootstrap_servers,
    security_protocol='PLAINTEXT',
    retries=5
)

def generate_system_power_message(db):
    message = db.get_message_by_name('ID268SystemPower')
    data = {}

    for signal in message.signals:
        signal_name = signal.name
        minimum = signal.minimum
        maximum = signal.maximum
        scale = signal.scale or 1
        length = signal.length
        is_unsigned = maximum >= 2 ** (length - 1)

        if length <= 8:  # 对于长度不超过8位的信号
            if is_unsigned:
                if signal_name == 'DI_primaryUnitSiliconType':  # 对于DI_primaryUnitSiliconType信号
                    random_value = random.randint(0, 2 ** length - 1)  # 确保值在0到255之间
                else:
                    random_value = 0  # 其他8位无符号信号暂时设置为0
            else:
                if signal_name == 'SystemRegenPowerMax268':  # 对于SystemRegenPowerMax268信号
                    random_value = random.randint(minimum, 0)  # 确保值在-155到0之间
                else:
                    random_value = random.randint(min(0, minimum), max(0, maximum))
        elif length <= 16:  # 对于长度在9-16位的信号
            if is_unsigned:
                if signal_name == 'SystemDrivePowerMax268':  # 对于SystemDrivePowerMax268信号
                    random_value = random.randint(minimum // scale, maximum // scale) * scale  # 确保值在0到2047之间
                else:
                    random_value = random.randint(minimum // scale, maximum // scale) * scale
            else:
                random_value = random.randint(min(0, minimum // scale), max(0, maximum // scale)) * scale
        else:  # 对于长度超过16位的信号
            random_value = random.uniform(minimum, maximum)

        data[signal_name] = random_value

    print('Signal Values:')
    for signal_name, value in data.items():
        print(f'{signal_name}: {value}')

    encoded_data = message.encode(data)
    print(f'\nEncoded Data: {encoded_data.hex()}')

    return encoded_data.hex().encode('utf-8')

if __name__ == '__main__':
    db = cantools.database.load_file('Model3CAN.dbc')

    while True:
        message = generate_system_power_message(db)
        producer.send(topic_name, message)
        time.sleep(1)  # 等待1秒后再次生成消息
