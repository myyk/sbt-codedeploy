package sbt.codedeploy

import sbt._
import sbt.Keys._

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.Regions

object Keys {
  val CodeDeploy = config("codedeploy")

  val codedeployAWSCredentialsProvider = settingKey[Option[AWSCredentialsProvider]]("AWS credentials provider used by AWS Code Deploy.")
  val codedeployBucket = settingKey[String]("S3 bucket used by AWS Code Deploy.")
  val codedeployClientConfiguration = settingKey[Option[ClientConfiguration]]("Client configuration used by AWS Code Deploy.")
  val codedeployRegion = settingKey[Regions]("AWS region used by AWS Code Deploy.")
  val codedeployIgnoreApplicationStopFailures = settingKey[Boolean]("Whether to ignore application stop failures during deploy.")

  val codedeployContentMappings = taskKey[Seq[ContentMapping]]("Mappings for code deploy content (i.e. the files section).")
  val codedeployPermissionMappings = taskKey[Seq[PermissionMapping]]("Specify code deploy permissions.")
  val codedeployPush = taskKey[Unit]("Push a revision to AWS Code Deploy.")
  val codedeployScriptMappings = taskKey[Seq[ScriptMapping]]("Mappings for code deploy hook scripts.")
  val codedeployStage = taskKey[Unit]("Stage a Code Deploy revision in the staging directory.")
  val codedeployStagingDirectory = taskKey[File]("Directory used to stage a Code Deploy revision.")
  val codedeployZip = taskKey[Unit]("Create a zip from a staged deployment.")
  val codedeployZipFile = taskKey[File]("Location of the deployment zip.")

  val codedeployCreateDeployment = inputKey[Unit]("Deploy to the given deployment group.")
}
