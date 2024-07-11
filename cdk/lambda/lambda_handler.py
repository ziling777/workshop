import boto3
import cfnresponse

def handler(event, context):
    # Init ...
    the_event = event['RequestType']
    print("The event is: ", str(the_event))
    response_data = {}
    s_3 = boto3.client('s3')
    # Retrieve parameters
    the_bucket = event['ResourceProperties']['the_bucket']
    try:
        if the_event in ('Create', 'Update'):
            with open('flink-java-project-0.1.jar', 'rb') as file_data:
                s_3.put_object(Bucket=the_bucket,
                               Key=('flink-application.jar'), Body=file_data)
        elif the_event == 'Delete':
            print("Deleting S3 content...")
            b_operator = boto3.resource('s3')
            b_operator.Bucket(str(the_bucket)).objects.all().delete()
        # Everything OK... send the signal back
        print("Operation successful!")
        cfnresponse.send(event,
                         context,
                         cfnresponse.SUCCESS,
                         response_data)
    except Exception as e:
        print("Operation failed...")
        print(str(e))
        response_data['Data'] = str(e)
        cfnresponse.send(event,
                         context,
                         cfnresponse.FAILED,
                         response_data)