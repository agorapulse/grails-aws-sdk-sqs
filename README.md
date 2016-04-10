AWS SDK SQS Grails Plugin
=========================

[![Build Status](https://travis-ci.org/agorapulse/grails-aws-sdk-sqs.svg?token=BpxbA1UyYnNoUwrDNXtN&branch=master)](https://travis-ci.org/agorapulse/grails-aws-sdk-sqs)

# Introduction

The AWS SDK Plugins allow your [Grails](http://grails.org) application to use the [Amazon Web Services](http://aws.amazon.com/) infrastructure services.
The aim is to provide lightweight utility Grails service wrappers around the official [AWS SDK for Java](http://aws.amazon.com/sdkforjava/).

The following services are currently supported:

* [AWS SDK CloudSearch Grails Plugin](http://github.com/agorapulse/grails-aws-sdk-cloudsearch)
* [AWS SDK DynamoDB Grails Plugin](http://github.com/agorapulse/grails-aws-sdk-dynamodb)
* [AWS SDK Kinesis Grails Plugin](http://github.com/agorapulse/grails-aws-sdk-kinesis)
* [AWS SDK S3 Grails Plugin](http://github.com/agorapulse/grails-aws-sdk-s3)
* [AWS SDK SES Grails Plugin](http://github.com/agorapulse/grails-aws-sdk-ses)
* [AWS SDK SQS Grails Plugin](http://github.com/agorapulse/grails-aws-sdk-sqs)

This plugin encapsulates **Amazon SQS** related logic.


# Installation

Add plugin dependency to your `build.gradle`:

```groovy
dependencies {
  ...
  compile 'org.grails.plugins:aws-sdk-sqs:2.0.0-beta1'
  ...
```


# Config

Create an AWS account [Amazon Web Services](http://aws.amazon.com/), in order to get your own credentials accessKey and secretKey.


## AWS SDK for Java version

You can override the default AWS SDK for Java version by setting it in your _gradle.properties_:

```
awsJavaSdkVersion=1.10.66
```

## Credentials

Add your AWS credentials parameters to your _grails-app/conf/application.yml_:

```yml
grails:
    plugin:
        awssdk:
            accessKey: {ACCESS_KEY}
            secretKey: {SECRET_KEY}
```

If you do not provide credentials, a credentials provider chain will be used that searches for credentials in this order:

* Environment Variables - `AWS_ACCESS_KEY_ID` and `AWS_SECRET_KEY`
* Java System Properties - `aws.accessKeyId and `aws.secretKey`
* Instance profile credentials delivered through the Amazon EC2 metadata service (IAM role)

## Region

The default region used is **us-east-1**. You might override it in your config:

```yml
grails:
    plugin:
        awssdk:
            region: eu-west-1
```

If you're using multiple AWS SDK Grails plugins, you can define specific settings for each services.

```yml
grails:
    plugin:
        awssdk:
            accessKey: {ACCESS_KEY} # Global default setting
            secretKey: {SECRET_KEY} # Global default setting
            region: us-east-1       # Global default setting
            sqs:
                accessKey: {ACCESS_KEY} # Service setting (optional)
                secretKey: {SECRET_KEY} # Service setting (optional)
                region: eu-west-1       # Service setting (optional)
                queueNamePrefix: ben_   # Service setting (optional)
            
```

**queueNamePrefix** allows you to automatically prefix all your queue names (for example, to get different env or scopes for each developer running their app locally).


# Usage

The plugin provides the following Grails artefact:

* **AmazonSQSService**

Usage examples:

```groovy
// Create queue
amazonSQSService.createQueue(queueName)

// List queue URLs
amazonSQSService.listQueueUrls()

// Delete message
amazonSQSService.deleteMessage(queueUrl, messageId)

// Get queue attribute
amazonSQSService.getQueueAttributes(queueUrl)

// Receive messages
amazonSQSService.receiveMessages(queueUrl, maxNumberOfMessages, visibilityTimeout, waitTimeSeconds)

// Send message
amazonSQSService.sendMessage(queueUrl, messageBody)
```

If required, you can also directly use **AmazonSQSClient** instance available at **amazonSQSService.client**.

For more info, AWS SDK for Java documentation is located here:

* [AWS SDK for Java](http://docs.amazonwebservices.com/AWSJavaSDK/latest/javadoc/index.html)


# Bugs

To report any bug, please use the project [Issues](http://github.com/agorapulse/grails-aws-sdk-sqs/issues) section on GitHub.

Feedback and pull requests are welcome!