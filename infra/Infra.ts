import * as cdk from "aws-cdk-lib";
import * as constructs from "constructs";
import * as lambda from "aws-cdk-lib/aws-lambda";
import * as iam from "aws-cdk-lib/aws-iam";
import * as s3 from "aws-cdk-lib/aws-s3";
import * as s3Deployement from "aws-cdk-lib/aws-s3-deployment";
import * as path from "path";

async function main() {
  const app = new cdk.App()
  new Stack(app)
  app.synth()
}

class Stack extends cdk.Stack {
  constructor(scope: constructs.Construct) {
    super(scope, "Infra", {
      env: {
        account: process.env.CDK_DEFAULT_ACCOUNT,
        region: process.env.CDK_DEFAULT_REGION,
      }
    })

    const webAssetBucket = new s3.Bucket(this, "WebAssetBucket", {
      bucketName: "payment-receipt-scanner-demo",
      websiteIndexDocument: "index.html",
      removalPolicy: cdk.RemovalPolicy.DESTROY,
      publicReadAccess: true,
      blockPublicAccess: {
        blockPublicAcls: false,
        blockPublicPolicy: false,
        ignorePublicAcls: false,
        restrictPublicBuckets: false,
      }
    })

    new s3Deployement.BucketDeployment(this, "BucketDeployment", {
      destinationBucket: webAssetBucket,
      sources: [s3Deployement.Source.asset(path.resolve(__dirname, "../web/dist"))],
    })

    const bucket = new s3.Bucket(this, "Bucket", {
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    })

    const fn = new lambda.Function(this, "Lambda", {
      runtime: lambda.Runtime.JAVA_17,
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS,
      handler: 'fi.rce.Lambda::handleRequest',
      code: lambda.Code.fromAsset("../server-assembly-0.1.0-SNAPSHOT.jar"),
      timeout: cdk.Duration.seconds(120),
      memorySize: 256,
      environment: {
        BUCKET_NAME: bucket.bucketName,
      }
    });
    fn.addToRolePolicy(new iam.PolicyStatement({
      actions: ["textract:*", "secretsmanager:*", "s3:*"],
      resources: ["*"],
    }))
    fn.addFunctionUrl({
      authType: lambda.FunctionUrlAuthType.NONE,
      cors: {
        allowedOrigins: ["*"],
        allowedMethods: [lambda.HttpMethod.GET, lambda.HttpMethod.POST],
      }
    })
  }
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
