package sbt.codedeploy

import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers

import scala.util._
import scala.collection.JavaConversions._

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.ClientConfiguration

import com.amazonaws.auth.AWSCredentialsProvider

import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions

import com.amazonaws.services.cloudformation.model.Stack

import com.amazonaws.services.codedeploy.AmazonCodeDeployClient
import com.amazonaws.services.codedeploy.model._

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

import com.github.tptodorov.sbt.cloudformation.CloudFormation
import com.github.tptodorov.sbt.cloudformation.CloudFormation.autoImport._

object CodeDeployPlugin extends AutoPlugin {
  object autoImport {
    val CodeDeploy = config("codedeploy")

    val codedeployAWSCredentialsProvider = settingKey[Option[AWSCredentialsProvider]]("AWS credentials provider used by AWS Code Deploy.")
    val codedeployBucket = settingKey[String]("S3 bucket used by AWS Code Deploy.")
    val codedeployClientConfiguration = settingKey[Option[ClientConfiguration]]("Client configuration used by AWS Code Deploy.")
    val codedeployRegion = settingKey[Regions]("AWS region used by AWS Code Deploy.")
    val codedeployIgnoreApplicationStopFailures = settingKey[Boolean]("Whether to ignore application stop failures during deploy.")

    val codedeployContentMappings = taskKey[Seq[ContentMapping]]("Mappings for code deploy content (i.e. the files section).")
    val codedeployPermissionMappings = taskKey[Seq[PermissionMapping]]("Specify code deploy permissions.")
    val codedeployScriptMappings = taskKey[Seq[ScriptMapping]]("Mappings for code deploy hook scripts.")
    val codedeployStage = taskKey[Unit]("Stage a Code Deploy revision in the staging directory.")
    val codedeployStagingDirectory = taskKey[File]("Directory used to stage a Code Deploy revision.")
    val codedeployZip = taskKey[Unit]("Create a zip from a staged deployment.")
    val codedeployZipFile = taskKey[File]("Location of the deployment zip.")

    val codedeployCreateApplication = taskKey[ApplicationInfo]("Create a new Application if it doesn't already exist in AWS Code Deploy.")
    val codedeployCreateDeploymentGroup = taskKey[DeploymentGroupInfo]("Create a new DeploymentGroup if it doesn't already exist in AWS Code Deploy.")
    val codedeployPush = taskKey[Unit]("Push a revision to AWS Code Deploy.")
    val codedeployCreateDeployment = inputKey[Unit]("Deploy to the given deployment group.")
  }
  import autoImport._

  override def requires = CloudFormation
  override def trigger = allRequirements

  override def projectSettings = Seq(
    codedeployAWSCredentialsProvider := None,
    codedeployClientConfiguration := None,
    codedeployContentMappings := ContentMapping.defaultMappings(
      (name in CodeDeploy).value,
      (sourceDirectory in CodeDeploy).value,
      Classpaths.managedJars(Compile, Set("jar"), update.value),
      packagedArtifact.in(Compile, packageBin).value,
      organization.value,
      version.value),
    codedeployCreateApplication := {
      getOrCreateApplication(
        createAWSClient(
          classOf[AmazonCodeDeployClient],
          (codedeployRegion in CodeDeploy).value,
          (codedeployAWSCredentialsProvider in CodeDeploy).value.orNull,
          (codedeployClientConfiguration in CodeDeploy).value.orNull
        ),
        (name in CodeDeploy).value,
        (streams in CodeDeploy).value.log
      )
    },
    codedeployIgnoreApplicationStopFailures := false,
    codedeployPermissionMappings := PermissionMapping.defaultMappings(
      (sourceDirectory in CodeDeploy).value),
    codedeployPush := {
      (codedeployZip in CodeDeploy).value
      val zipFile = (codedeployZipFile in CodeDeploy).value
      pushImpl(
        createAWSClient(
          classOf[AmazonCodeDeployClient],
          (codedeployRegion in CodeDeploy).value,
          (codedeployAWSCredentialsProvider in CodeDeploy).value.orNull,
          (codedeployClientConfiguration in CodeDeploy).value.orNull          
        ),
        createAWSClient(
          classOf[AmazonS3Client],
          (codedeployRegion in CodeDeploy).value,
          (codedeployAWSCredentialsProvider in CodeDeploy).value.orNull,
          (codedeployClientConfiguration in CodeDeploy).value .orNull         
        ),
        zipFile,
        (codedeployBucket in CodeDeploy).value,
        (name in CodeDeploy).value,
        (version in CodeDeploy).value,
        (streams in CodeDeploy).value.log
      )
    },
    codedeployRegion := {
      val region = (stackRegion in CodeDeploy).value
      Regions.fromName(region)
    },
    codedeployScriptMappings := ScriptMapping.defaultMappings(
      (sourceDirectory in CodeDeploy).value),
    codedeployStage := {
      val deployment = (codedeployStagingDirectory in CodeDeploy).value
      streams.value.log.info(s"Staging deployment in ${deployment}...")
      stageRevision(
        deployment = deployment,
        name = (name in CodeDeploy).value,
        version = (version in CodeDeploy).value,
        content = (codedeployContentMappings in CodeDeploy).value.map {
          content => {
            val source = content.source
            val prefix = if (source.startsWith("/")) { "content" } else { "content/" }
            content.copy(source = s"${prefix}${content.source}")
          }
        },
        scripts = (codedeployScriptMappings in CodeDeploy).value.map {
          script => {
            val location = script.location
            val prefix = if (location.startsWith("/")) { "scripts" } else { "scripts/" }
            script.copy(location = s"${prefix}${script.location}")
          }
        },
        permissions = (codedeployPermissionMappings in CodeDeploy).value
      )
    },
    codedeployStagingDirectory := (target in CodeDeploy).value / "stage",
    codedeployZip := {
      (codedeployStage in CodeDeploy).value
      val deployment = (codedeployStagingDirectory in CodeDeploy).value
      val zipFile = (codedeployZipFile in CodeDeploy).value
      streams.value.log.info(s"Generating deployment zip in ${zipFile}...")
      IO.zip(sbt.Path.allSubpaths(deployment), zipFile)
    },
    codedeployZipFile := {
      (target in CodeDeploy).value / s"${name.value}-${version.value}.zip"
    },
    sourceDirectory in CodeDeploy := {
      (baseDirectory in CodeDeploy).value / "src" / "codedeploy"
    },
    target in CodeDeploy := target.value / "codedeploy"
  ) ++ makeCodeDeployConfig(Staging) ++ makeCodeDeployConfig(Production)

  def makeCodeDeployConfig(config: Configuration) = Seq(
    codedeployCreateDeployment in config := {
      // The Application and DeploymentGroup should be created in CodeDeploy first
      codedeployCreateApplication.value
      (codedeployCreateDeploymentGroup in config).value

      deploy(
        createAWSClient(
          classOf[AmazonCodeDeployClient],
          (codedeployRegion in CodeDeploy).value,
          (codedeployAWSCredentialsProvider in CodeDeploy).value.orNull,
          (codedeployClientConfiguration in CodeDeploy).value.orNull
        ),
        (name in CodeDeploy).value,
        (codedeployBucket in CodeDeploy).value,
        (version in CodeDeploy).value,
        config.name,
        (codedeployIgnoreApplicationStopFailures in CodeDeploy).value,
        (streams in CodeDeploy).value.log
      ).getDeploymentId
    },
    codedeployCreateDeploymentGroup in config := {
      val stack = (stackDescribe in config).value.getOrElse(throw new IllegalStateException(s"Stack ${config.name} does not exists, but should exist. Try running: ${config.name}:createStack"))
      getOrCreateDeploymentGroup(
        createAWSClient(
          classOf[AmazonCodeDeployClient],
          (codedeployRegion in CodeDeploy).value,
          (codedeployAWSCredentialsProvider in CodeDeploy).value.orNull,
          (codedeployClientConfiguration in CodeDeploy).value.orNull
        ),
        (name in CodeDeploy).value,
        config.name,
        stack,
        (streams in CodeDeploy).value.log
      )
    }
  )

  private val deployArgsParser = {
    import DefaultParsers._
    Space ~> token(ID, "<deployment-group-name> (e.g. staging, production)")
  }

  private def r(cmd: ProcessBuilder): Unit = {
    assert(0 == cmd.!, s"command failed: ${cmd}")
  }

  private def createAWSClient[T <: AmazonWebServiceClient](
    clazz: Class[T],
    regions: Regions,
    credentialsProvider: AWSCredentialsProvider,
    clientConfiguration: ClientConfiguration
  ): T = {
    Region.getRegion(regions).createClient(clazz, credentialsProvider, clientConfiguration)
  }

  private def s3Location(
    bucket: String,
    name: String,
    version: String
  ): S3Location = {
    new S3Location()
      .withBucket(bucket)
      .withKey(s3Key(name, version))
      .withBundleType(BundleType.Zip)
  }

  private def s3Key(
    name: String,
    version: String
  ): String = {
    s"${name}/codedeploy-revisions/${version}.zip"
  }

  private def getOrCreateApplication(
    codeDeployClient: AmazonCodeDeployClient,
    name: String,
    log: Logger
  ): ApplicationInfo = {
    val existingApplication = Try(codeDeployClient.getApplication(
        new GetApplicationRequest()
          .withApplicationName(name)
      ).getApplication
    )

    existingApplication match {
      case Success(existingApp) =>
        log.info(s"Application $name in exists in CodeDeploy.")
        existingApp
      case Failure(ex: ApplicationDoesNotExistException) =>
        log.info(s"Creating application $name in CodeDeploy.")
        codeDeployClient.createApplication(
          new CreateApplicationRequest()
            .withApplicationName(name)
        )
        log.info(s"Created application $name in CodeDeploy successfully.")

        getOrCreateApplication(codeDeployClient, name, log)
      case Failure(ex) =>
        throw ex
    }
  }

  private def getOrCreateDeploymentGroup(
    codeDeployClient: AmazonCodeDeployClient,
    applicationName: String,
    deploymentGroupName: String,
    stack: Stack,
    log: Logger
  ): DeploymentGroupInfo = {
    val existingDeploymentGroup = Try(codeDeployClient.getDeploymentGroup(
        new GetDeploymentGroupRequest()
          .withApplicationName(applicationName)
          .withDeploymentGroupName(deploymentGroupName)
      ).getDeploymentGroupInfo
    )

    existingDeploymentGroup match {
      case Success(existingDeploymentGroup) =>
        log.info(s"DeploymentGroup $deploymentGroupName of Applicaton $applicationName exists in CodeDeploy.")
        existingDeploymentGroup
      case Failure(ex: DeploymentGroupDoesNotExistException) =>
        log.info(s"Creating DeploymentGroup $deploymentGroupName of Applicaton $applicationName in CodeDeploy.")
        val autoScalingGroup = getStackOutput("AutoScalingGroupArn", stack)
        val serviceRoleArn = getStackOutput("CodeDeployTrustRoleArn", stack)

        codeDeployClient.createDeploymentGroup(
          new CreateDeploymentGroupRequest()
            .withApplicationName(applicationName)
            .withDeploymentGroupName(deploymentGroupName)
            .withAutoScalingGroups(autoScalingGroup)
            .withServiceRoleArn(serviceRoleArn)
        )
        log.info(s"Created DeploymentGroup $deploymentGroupName of Applicaton $applicationName in CodeDeploy successfully.")

        getOrCreateDeploymentGroup(codeDeployClient, applicationName, deploymentGroupName, stack, log)
      case Failure(ex) =>
        throw ex
    }
  }

  private def getStackOutput(
    key: String,
    stack: Stack
  ): String = {
    stack.getOutputs.find(_.getOutputKey == key).map(_.getOutputValue).getOrElse(throw new IllegalStateException(s"Stack requires an output with key = [$key]."))
  }

  private def deploy(
    codeDeployClient: AmazonCodeDeployClient,
    name: String,
    s3Bucket: String,
    version: String,
    groupName: String,
    ignoreApplicationStopFailures: Boolean,
    log: Logger
  ): CreateDeploymentResult = {
    val revisions = codeDeployClient.listApplicationRevisions(
      new ListApplicationRevisionsRequest()
        .withApplicationName(name)
        .withS3Bucket(s3Bucket)
        .withS3KeyPrefix(s3Key(name, version))
        .withSortBy(ApplicationRevisionSortBy.RegisterTime)
        .withSortOrder(SortOrder.Descending))
      .getRevisions

    val revision = revisions.headOption.getOrElse(sys.error(s"There were no registered application revisions at ${s3Location(s3Bucket, name, version)}"))
    log.info(s"Using application revision ${revision.toString}")

    if (ignoreApplicationStopFailures) {
      log.warn(s"This deployment will ignore ApplicationStop failures.")
    }
    val result = codeDeployClient.createDeployment(
      new CreateDeploymentRequest()
        .withApplicationName(name)
        .withDeploymentGroupName(groupName)
        .withRevision(revision)
        .withIgnoreApplicationStopFailures(ignoreApplicationStopFailures))
    log.info(s"Created deployment ${result.getDeploymentId}")
    result
  }

  private def pushImpl(
    codeDeployClient: AmazonCodeDeployClient,
    s3Client: AmazonS3Client,
    zipFile: File,
    s3Bucket: String,
    name: String,
    version: String,
    log: Logger
  ): S3Location = {
    val key = s3Key(name, version)
    // the upload can be slow, so be sure to let
    // the user know it is happening
    log.info(s"Uploading zip to s3://${s3Bucket}/${key}...")

    val putObjectResult = s3Client.putObject(
      new PutObjectRequest(s3Bucket, key, zipFile))
    val s3Loc = s3Location(s3Bucket, name, version)
      .withETag(putObjectResult.getETag)

    codeDeployClient.registerApplicationRevision(
      new RegisterApplicationRevisionRequest()
        .withApplicationName(name)
        .withRevision(new RevisionLocation()
          .withRevisionType(RevisionLocationType.S3)
          .withS3Location(s3Loc)))

    log.info(s"Uploaded bundle ${s3Loc}")

    s3Loc
  }

  private def stageRevision(
    deployment: File,
    name: String,
    version: String,
    content: Seq[ContentMapping],
    scripts: Seq[ScriptMapping],
    permissions: Seq[PermissionMapping]
  ): Unit = {

    IO.delete(deployment)
    deployment.mkdirs

    IO.copy(content.map { content =>
      content.file -> deployment / content.source
    })

    IO.copy(scripts.map { script =>
      script.file -> deployment / script.location
    })

    IO.write(deployment / "appspec.yml",
      generateAppSpec(content, scripts, permissions))
  }

  private def generateAppSpec(
    content: Seq[ContentMapping],
    scripts: Seq[ScriptMapping],
    permissions: Seq[PermissionMapping]
  ): String = {
    val appspec = new StringBuilder

    appspec ++= "version: 0.0\n"
    appspec ++= "os: linux\n"
    generateAppSpecHooks(appspec, scripts)
    generateAppSpecFiles(appspec, content)
    generateAppSpecPermissions(appspec, permissions)

    appspec.result
  }

  private def generateAppSpecHooks(
    appspec: StringBuilder,
    scripts: Seq[ScriptMapping]
  ): Unit = {
    appspec ++= "hooks:\n"
    // sort includes location so that scripts for
    // each hook will run in lexicograpic order
    for {
      (section, scripts) <- scripts.groupBy(_.section).toSeq.sortBy { case (section, _) => section }
    } {
      appspec ++=   s"  ${section}:\n"
      
      scripts.sortBy(script => script.location).foreach { script =>
        appspec ++= s"""    - location: ${script.location}\n"""
        appspec ++= s"""      timeout: ${script.timeout}\n"""
        appspec ++= s"""      runas: ${script.runas}\n"""
      }
    }
  }

  private def generateAppSpecFiles(
    appspec: StringBuilder,
    content: Seq[ContentMapping]
  ): Unit = {
    appspec ++= "files:\n"
    content.foreach { content =>
      appspec ++= s"""  - source: ${content.source}\n"""
      appspec ++= s"""    destination: ${content.destination}\n"""
    }
  }

  private def generateAppSpecPermissions(
    appspec: StringBuilder,
    permissions: Seq[PermissionMapping]
  ): Unit = {
    appspec ++= "permissions:\n"
    permissions.foreach { permission =>
      appspec ++= s"""  - object: ${permission.objectPath}\n"""
      appspec ++= s"""    mode: "${permission.mode}"\n"""
      appspec ++= s"""    owner: ${permission.owner}\n"""
      appspec ++= s"""    group: ${permission.group}\n"""
      permission.objectType.foreach { objectType =>
        appspec ++= s"""    type:\n"""
        appspec ++= s"""      - $objectType\n"""
      }
    }
  }
}
