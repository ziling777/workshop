import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as msk from 'aws-cdk-lib/aws-msk';
import * as s3 from 'aws-cdk-lib/aws-s3';
import * as emr from 'aws-cdk-lib/aws-emr';
import * as cloud9 from 'aws-cdk-lib/aws-cloud9';

import * as kinesisanalytics from 'aws-cdk-lib/aws-kinesisanalyticsv2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as customResources from 'aws-cdk-lib/custom-resources';

import { Construct } from 'constructs';



export class CdkStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const vpc = new ec2.Vpc(this, 'MMVPC', {
      restrictDefaultSecurityGroup: false,
      natGateways: 1
    });

    // Fetch the default security group ID
    const defaultSecurityGroupId = vpc.vpcDefaultSecurityGroup;

    const securityGroup = ec2.SecurityGroup.fromSecurityGroupId(this, 'ImportedSecurityGroup', defaultSecurityGroupId);

    // Update inbound rule
    securityGroup.addIngressRule(
      ec2.Peer.ipv4(vpc.vpcCidrBlock),
      ec2.Port.allTraffic(),
      'Allow internal access within the VPC'
    );

    const privateSubnetIds = vpc.selectSubnets({
      subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS
    }).subnetIds;

    const mskCluster = new msk.CfnCluster(this, 'MyMskCluster', {
      clusterName: 'my-msk-cluster',
      kafkaVersion: '3.5.1',
      numberOfBrokerNodes: 2,
      brokerNodeGroupInfo: {
        instanceType: 'kafka.m5.large',
        clientSubnets: privateSubnetIds,
        securityGroups: [defaultSecurityGroupId],
        // Specify storage volume information (optional)
        storageInfo: {
          ebsStorageInfo: {
            volumeSize: 1000, // 1000 GiB
          },
        },
      },
      clientAuthentication:
      {
        unauthenticated: {
            enabled: true
        }
      },
      encryptionInfo: {
        encryptionInTransit: {
          inCluster: true,
          clientBroker: 'PLAINTEXT'
        }
      }

      // Specify additional configurations (optional)
    });


    // Define S3 bucket for Managed Flink, EMR, etc.
    const s3Bucket = new s3.Bucket(this, 'gcrvdpbucket', {
      // Bucket configuration options
      removalPolicy: cdk.RemovalPolicy.DESTROY, // Automatically delete bucket when stack is deleted
      autoDeleteObjects: true, // Automatically delete objects within the bucket when the bucket is deleted
    });

    const kinesisAnalyticsRole = new iam.Role(this, 'KinesisAnalyticsRole', {
      assumedBy: new iam.ServicePrincipal('kinesisanalytics.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AdministratorAccess'),
      ]
    });

    const kdaApplication = new kinesisanalytics.CfnApplication(this, 'MyKDAApplication', {
      applicationName: "parsesignal",
      runtimeEnvironment: 'FLINK-1_18',
      serviceExecutionRole: kinesisAnalyticsRole.roleArn,
      applicationConfiguration: {
        applicationCodeConfiguration: {
          codeContent: {
            s3ContentLocation: {

              bucketArn: s3Bucket.bucketArn,
              fileKey: 'flink-application.jar',
            },
          },
          codeContentType: 'ZIPFILE',
        },
        environmentProperties: {
          propertyGroups: [
            {
              propertyGroupId: 'flinkapp',
              propertyMap: {
                'MSKBOOTSTRAP_SERVERS_KEY': '<place-holder>'
              },
            },
            {
              propertyGroupId: 'VpcConfiguration',
              propertyMap: {
                VpcId: vpc.vpcId,
                SubnetIds: vpc.selectSubnets().subnetIds.join(','),
                SecurityGroupIds: [securityGroup.securityGroupId].join(','),
              },
            }
          ],
        },
      }
    });

    // Define the IAM role for the Lambda function
    const lambdaExecutionRole = new iam.Role(this, 'CustomResourceLambdaExecutionRole', {
      assumedBy: new iam.ServicePrincipal('lambda.amazonaws.com'),
      roleName: cdk.Fn.sub('${AWS::StackName}-${AWS::Region}-AWSLambdaExecutionRole'),
      path: '/',
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AdministratorAccess'),
      ]
    });

    const customResourceLambdaFunction = new lambda.Function(this, 'CustomResourceLambdaFunction', {
      code: lambda.Code.fromAsset('../cdk/lambda'),
      handler: 'lambda_handler.handler',
      runtime: lambda.Runtime.PYTHON_3_12,
      role: lambdaExecutionRole,
      description: "Work with S3 Buckets!",
      functionName: cdk.Fn.sub('${AWS::StackName}-${AWS::Region}-lambda'),
      timeout: cdk.Duration.seconds(900),
    });

    const provider = new customResources.Provider(this, 'CustomResourceProvider', {
      onEventHandler: customResourceLambdaFunction,
    });

    const s3customResource = new cdk.CustomResource(this, 'S3CustomResource', {
      serviceToken: provider.serviceToken,
      properties: {
        the_bucket: s3Bucket.bucketName,
        content: 'This is the content of the uploaded object.',
      },
    });


    kdaApplication.node.addDependency(s3customResource);


    const emrEc2Role = new iam.Role(this, 'EmrEc2Role', {
      roleName: 'EMR_EC2_DefaultRole',
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AdministratorAccess')
      ]
    });

    const instanceProfile = new iam.CfnInstanceProfile(this, "EMRInstanceProfile", {
      roles: [emrEc2Role.roleName],
      instanceProfileName: 'EMR_EC2_DefaultRole'
    });

    const instanceProfileName = instanceProfile.instanceProfileName;

    const emrClusterRole = new iam.Role(this, 'EmrClusterRole', {
      assumedBy: new iam.ServicePrincipal('elasticmapreduce.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AdministratorAccess')
      ]
    });

    const selectedSubnets = vpc.selectSubnets({
      subnetType: ec2.SubnetType.PUBLIC, // Change to your desired subnet type
    });


    // emr created security group will prevent cloudformation from deleting, due to the issue
    // https://github.com/MoonsetJS/Moonset/issues/21
    const cluster = new emr.CfnCluster(this, 'vdpcluster', {
      instances: {
        ec2SubnetId: selectedSubnets.subnetIds[0],
        masterInstanceGroup: {
          instanceCount: 1,
          instanceType: 'm5.2xlarge',
          market: 'ON_DEMAND'
        },
        coreInstanceGroup: {
          instanceCount: 1,
          instanceType: 'm5.xlarge',
          market: 'ON_DEMAND'
        }
      },
      name: 'MyEMRCluster',
      jobFlowRole: instanceProfileName ?? 'EMR_EC2_DefaultRole',
      serviceRole: emrClusterRole.roleName, // Ensure this role exists in your account
      releaseLabel: 'emr-6.5.0',
      applications: [{ name: 'HBase' }, { name: 'Flink' }] // Example applications
    });

    cluster.node.addDependency(instanceProfile);

    
    // Create the Cloud9 environment
    const cloud9Instance = new cloud9.CfnEnvironmentEC2(this, 'vdpcloud9', {

      instanceType: 'm5.large', // Specify the instance type
      imageId: 'amazonlinux-2-x86_64', // Replace with the desired image ID
      subnetId: selectedSubnets.subnetIds[0], // Specify the subnet ID
      automaticStopTimeMinutes: 20160,
      connectionType: 'CONNECT_SSH',
      // TODO: have to hard code the owner arn here, will not work if the env is not from workshop studio
      ownerArn: "arn:aws:sts::" + this.account + ":assumed-role/WSParticipantRole/Participant"
      

      // Uncomment the following line to associate the IAM role created above
      // with the Cloud9 environment
      // automaticStopTimeMinutes: 30, // Automatically stop the environment after a specified time of inactivity
      // ownerArn: cloud9Role.roleArn, // Associate the IAM role with the Cloud9 environment
    });
  }
}
