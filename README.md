# SBT plugin for [AWS CodeDeploy](http://aws.amazon.com/codedeploy/)

Creates zip files for usage with AWS CodeDeploy. Uses defaults that support conventions set forth in the AWS CodeDeploy docs.

[![Build Status](https://travis-ci.org/gilt/sbt-codedeploy.svg)](https://travis-ci.org/search/sbt-codedeploy)
[![Codacy Badge](https://www.codacy.com/project/badge/88654d225b494663820bd3fec9bcf8a7)](https://www.codacy.com/public/myykseok/sbt-codedeploy)

# WARNING
This plugin intereacts with AWS resources. You will be billed for the AWS resources used by using this plugin. 

# Installation

Add the following to your `project/plugins.sbt` file:

    resolvers += Resolver.url("myyk-bintray-sbt-plugins", url("https://dl.bintray.com/myyk/sbt-plugins/"))(Resolver.ivyStylePatterns)
    addSbtPlugin("com.github.tptodorov" % "sbt-cloudformation" % "0.7.1")
    addSbtPlugin("com.github.myyk" % "sbt-codedeploy" % "0.6.0")

SBT CodeDeploy uses the AWS CodeDeploy API to upload the zip to a S3 Bucket (single region-only). You must specify the bucket in your `build.sbt` or `Build.scala`:

    codedeployBucket in ThisBuild := "your-codedeploy-bucket-name-here"

You must specify the region in your `build.sbt` or `Build.scala`:

    stackRegion := "us-east-1"

## AWS Setup

You'll also need to have AWS Credentials setup with enough privileges for the sbt-codedeploy to modify your AWS resources. Some great resources for this are::
- [AWS CodeDeploy Getting Started](http://docs.aws.amazon.com/codedeploy/latest/userguide/getting-started-setup.html)
- [AWS CLI Credentials setup](http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)

# Conventions

sbt-codedeploy makes use of defaults conventions to make configuring your application easy. Though we find them useful, you may override them to fit your needs.

A simple project might have structure like this in addition to it's regular project files.

    /src/codedeploy/content/
    /src/codedeploy/scripts/ApplicationStop/
    /src/codedeploy/scripts/BeforeInstall/
    /src/codedeploy/scripts/AfterInstall/
    /src/codedeploy/scripts/ApplicationStart/
    /src/codedeploy/scripts/ValidateService/

## Source Directory

This is the folder where 'content' and `scripts` should exist.

Default: /src/codedeploy/

Override: sourceDirectory in CodeDeploy

## AppSpec File location

It's assumed that the appspec will be generated by the plugin unless a location is specified. The file will be placed in the root directory of the application's source content's directory structure. By default this is `appspec.yml`.

Default: None

Override: `codedeployAppSpecLocation`

## Content

The content will be put into the zip at `content/`.

Defaults: `{sourceDirectory in CodeDeploy}/content`

## Scripts

The scripts will be put into the zip at `scripts/`.

The plugin is expecting scripts to be located in directories named after the hook they will be used with. If generating the appspec.yml the scripts will be run in an alphabetical ordering. We suggest putting numbers before the script name for easy ordering such as `200KillZombieChildren.sh` and `666InstallDaemons.sh` which would run in that order.

    scripts/ApplicationStop/
    scripts/BeforeInstall/
    scripts/AfterInstall/
    scripts/ApplicationStart/
    scripts/ValidateService/

Defaults: `{sourceDirectory in CodeDeploy}/scripts`

## Application Name

The default value is the project's name.

Override like this:

    name in CodeDeploy := "new-better-shinier-name"

## AWS Credentials Provider

The AWS credentials provider that will be used to get credentials to make AWS API calls.

Default: "None"

Override: `codedeployAWSCredentialsProvider`

If overriding, probably want to override it like this in most cases:

    import com.amazonaws.auth.profile.ProfileCredentialsProvider
    codedeployAWSCredentialsProvider := Some(new ProfileCredentialsProvider("myprofile"))

## Deployment IgnoreApplicationStopFailures

This is a setting used during deployments. It's documented in AWS's CodeDeploy docs here: [DeploymentInfo IgnoreApplicationStopFailures(http://docs.aws.amazon.com/codedeploy/latest/APIReference/API_DeploymentInfo.html#-Type-DeploymentInfo-IgnoreApplicationStopFailures)]

Default: false

Override: `codedeployIgnoreApplicationStopFailures`

## DeploymentGroups

There is an sbt configuration tied to each DeploymentGroup. `staging` and `production` are provided. If you need anything else you just add this to your `build.sbt`. You can add as many DeploymentGroups as you need.

    lazy val DeploymentGroupName = config("deploymentGroupName")
    GiltCodedeployPlugin.makeCodeDeployConfig(DeploymentGroupName)

If these configs are common with the `sbt-cloudformation` configs, they can be used to create DeploymentGroups through the plugin. In the CloudFormation stack associated with the config, the outputs `"AutoScalingGroupArn"` and `"CodeDeployTrustRoleArm"` must be set.

# Usage

## Push

If you just want to create the zip and push it to your configured S3 bucket use this.

    sbt codedeployPush

## Create Deployment

To create a deployment the `codedeployCreateDeployment` task needs the name of the CodeDeploy DeploymentGroup to deploy to. You must call `codedeployPush` first to make sure the application revision is available.

    sbt “staging:codedeployCreateDeployment”

Or

    sbt “production:codedeployCreateDeployment”

You may want to create a new alias such as `deploy-staging` to clean up the verbose syntax for your specific use case.

Note: `codedeployCreateDeployment` will create an Application if one does not already exist for the project. It will also try to create a DeploymentGroup if one doesn't already exist with the same name as the config, such as `production`.

**This call does not block for the deployment to finish.**

# Advanced Overriding

## AWS Region

By default, uses the region used by `sbt-cloudformation`. 

To override this behavior in `build.sbt` or `Build.scala`:

    codedeployRegion := com.amazonaws.regions.Regions.US_WEST_2

## Native Packager

See [sbt-native-packager-example](sbt-native-packager-example/README.md) for an example of how to use with sbt-native-packager.

## Content Mappings

If the default content mappings above are not enough you can override them. For example, maybe there is content in multiple places of the classpath that you would like included instead of the one place specified. You can override: `codedeployContentMappings`.

## Script Mappings

If the default script mappings above are not enough you can override them. For example, maybe there are scripts in multiple places of the classpath that you would like included instead of the one place specified. You can override: `codedeployScriptMappings`.

# FAQ

## I'm getting an error like this:

    [error] (*:codedeployPush) com.amazonaws.services.codedeploy.model.ApplicationDoesNotExistException: Applications not found for xxxxxxxxxxxx (Service: AmazonCodeDeploy; Status Code: 400; Error Code: ApplicationDoesNotExistException; Request ID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)

Check that the name matches your CodeDeploy application name `sbt inspect CodeDeploy:name`.

If that's fine then, maybe the AWS region is not correct. Follow the steps above to override the default.

## My deployment keeps failing.

Make sure you've got an zip of the application revision in your S3 bucket.

## My revision is in S3 but my deployment keeps failing.

There is a bug as of now (2015-Mar) with using multi-region S3 buckets. You can only use a bucket that's only in the instance that your EC2 instances are in currently.
