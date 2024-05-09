from pyflink.common import SimpleStringEncoder
from pyflink.datastream import StreamExecutionEnvironment
from pyflink.datastream.connectors import FlinkKafkaConsumer
from pyflink.common.typeinfo import Types
from pyflink.datastream.functions import FlatMapFunction
from pyflink.util.java_utils import load_java_app_class
from pyflink.common.serialization import SimpleStringSchema
from pyflink.datastream.connectors.fs.bucketing import BucketingSink, DateTimeBucketer
from pyflink.datastream.connectors.fs.writers import PartitionComputer
from cantools import database
import cantools

from cantools import database
import cantools

class CanMessageDecoder:
    def decode_can_message(self, db, encoded_message):
        message = db.get_message_by_name("ID268SystemPower")
        bytes_data = bytearray.fromhex(encoded_message)
        return message.decode(bytes_data)

    def flat_map(self, encoded_message):
        db = database.load_file("Model3CAN.dbc")
        decoded_message = self.decode_can_message(db, encoded_message)
        for key, value in decoded_message.items():
            yield key, str(value)

    def run(self, params):
        env = StreamExecutionEnvironment.get_execution_environment()

        properties = {
            "bootstrap.servers": params.get("bootstrap.servers"),
            "group.id": params.get("group.id")
        }

        kafka_consumer = FlinkKafkaConsumer(
            params.get("topic"),
            SimpleStringSchema(),
            properties
        )

        message_stream = env.add_source(kafka_consumer)

        kv_stream = message_stream.flat_map(
            FlatMapFunction.wrap_func(self.flat_map),
            output_type=Types.TUPLE([Types.STRING(), Types.STRING()])
        )

        bucketing_sink = BucketingSink(
            base_path=params.get("output.path"),
            bucketer=DateTimeBucketer(),
            batch_size=10 * 1024 * 1024,  # 10 MB
            inactive_bucket_threshold=60 * 1000,  # 1 minute
            descriptor="s3-sink",
            part_prefix="can-messages",
            partition_computer=PartitionComputer.date_time_computer()
        )

        kv_stream.add_sink(bucketing_sink)

        env.execute("CAN Message Decoder")

if __name__ == "__main__":
    decoder = CanMessageDecoder()
    params = {
        "dbc.file": "Model3CAN.dbc",
        "bootstrap.servers": "b-3.mskclustermskconnectla.rvmf3c.c7.kafka.us-east-2.amazonaws.com:9092",
        "group.id": "can-message-decoder",
        "topic": "ID268SystemPower",
        "output.path": "s3://canbusdemo/data/"
    }
    decoder.run(params)
