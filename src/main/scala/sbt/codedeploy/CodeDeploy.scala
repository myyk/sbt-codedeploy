package sbt.codedeploy

import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers

import scala.collection.JavaConversions._

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.ClientConfiguration

import com.amazonaws.auth.AWSCredentialsProvider

import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions

import com.amazonaws.services.codedeploy.AmazonCodeDeployClient
import com.amazonaws.services.codedeploy.model._

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

object CodeDeployPlugin extends AutoPlugin {
  import sbt.codedeploy.Keys._

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
    codedeployCreateDeployment := {
      val groupName = deployArgsParser.parsed
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
        groupName,
        (codedeployIgnoreApplicationStopFailures in CodeDeploy).value,
        (streams in CodeDeploy).value.log
      ).getDeploymentId
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
    codedeployRegion := Regions.US_EAST_1,
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
