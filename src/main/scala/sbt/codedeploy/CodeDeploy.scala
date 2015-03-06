package sbt.codedeploy

import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.ClientConfiguration

import com.amazonaws.auth.AWSCredentialsProvider

import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions

import com.amazonaws.services.codedeploy.AmazonCodeDeployClient
import com.amazonaws.services.codedeploy.model._

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._

case class CodeDeployContentMapping(
  localSource: File,
  destination: String,
  mode: String,
  owner: String,
  group: String,
  symlinkSource: Option[String]
) {
  def copyable: Boolean = {
    // instead of copying the directory we have to
    // create it as part of the deploy.
    // otherwise, codedeploy will just try to copy the source
    // into what it assumes is an existing destination directory
    if (isDirectory) return false
    // symlinks simply need to be created
    // after the install process
    else if (isSymlink) return false
    else true
  }

  def isSymlink = symlinkSource.isDefined

  def isDirectory = localSource.isDirectory
}

case class CodeDeployScriptMapping(
  source: File,
  section: String,
  location: String,
  timeout: Int,
  runas: String
) {
  import CodeDeployScriptMapping._

  require(
    ValidSections.contains(section),
    s"Section must be one of ${ValidSections.mkString("[", ", ", "]")}, not ${section}"
  )
}

object CodeDeployScriptMapping {
  private val ValidSections = Array(
    "ApplicationStop",
    "BeforeInstall",
    "AfterInstall",
    "ApplicationStart",
    "ValidateService"
  )
}

object CodeDeployKeys {
  val CodeDeploy = config("codedeploy")
  val codedeployRegion = settingKey[Regions]("AWS region used by AWS Code Deploy.")
  val codedeployAWSCredentialsProvider = settingKey[Option[AWSCredentialsProvider]]("AWS credentials provider used by AWS Code Deploy.")
  val codedeployClientConfiguration = settingKey[Option[ClientConfiguration]]("Client configuration used by AWS Code Deploy.")
  val codedeployBucket = settingKey[String]("S3 bucket used by AWS Code Deploy.")
  val codedeployIgnoreApplicationStopFailures = settingKey[Boolean]("Whether to ignore application stop failures during deploy.")

  val codedeployContentMappings = taskKey[Seq[CodeDeployContentMapping]]("Mappings for code deploy content (i.e. the files section).")
  val codedeployGenerateAppSpec = taskKey[String]("Generate the content of appspec.yml.")
  val codedeployPush = taskKey[Unit]("Push a revision to AWS Code Deploy.")
  val codedeployScriptMappings = taskKey[Seq[CodeDeployScriptMapping]]("Mappings for code deploy hook scripts.")
  val codedeployStage = taskKey[Unit]("Stage a Code Deploy revision in the staging directory.")
  val codedeployStagingDirectory = taskKey[File]("Directory used to stage a Code Deploy revision.")
  val codedeployZip = taskKey[Unit]("Create a zip from a staged deployment.")
  val codedeployZipFile = taskKey[File]("Location of the deployment zip.")

  val codedeployCreateDeployment = inputKey[Unit]("Deploy to the given deployment group.")
}

object CodeDeployPlugin extends AutoPlugin {
  import CodeDeployKeys._

  override def projectSettings = Seq(
    codedeployRegion := Regions.US_EAST_1,
    codedeployAWSCredentialsProvider := None,
    codedeployClientConfiguration := None,
    codedeployStagingDirectory := (target in CodeDeploy).value / "stage",
    codedeployIgnoreApplicationStopFailures := false,
    codedeployContentMappings := {
      val content = (sourceDirectory in CodeDeploy).value / "content"
      val relativize = Path.relativeTo(content)
      (content ***).get.filter(_.isFile).map { file =>
        relativize(file) match {
          case None =>
            sys.error(s"failed to relativize ${file} under ${content}")
          case Some(path) =>
            val section = path.split(Path.sep).head
            new CodeDeployContentMapping(
              localSource = file,
              destination = s"${ContentPrefix}${Path.sep}${path}",
              mode = "0644", // TODO
              owner = "root",
              group = "root",
              symlinkSource = None
            )
        }
      }
    },
    codedeployScriptMappings := {
      val scripts = (sourceDirectory in CodeDeploy).value / "scripts"
      val relativize = Path.relativeTo(scripts)
      (scripts ***).get.filter(_.isFile).map { file =>
        relativize(file) match {
          case None =>
            sys.error(s"failed to relativize ${file} under ${scripts}")
          case Some(path) =>
            val section = path.split(Path.sep).head
            new CodeDeployScriptMapping(
              source = file,
              section = section,
              location = path,
              timeout = 300,
              runas = "root"
            )
        }
      }
    },
    sourceDirectory in CodeDeploy := {
      (baseDirectory in CodeDeploy).value / "src" / "codedeploy"
    },
    codedeployGenerateAppSpec := generateAppSpec(
      content = (codedeployContentMappings in CodeDeploy).value,
      scripts = (codedeployScriptMappings in CodeDeploy).value
    ),
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
    codedeployStage := {
      val deployment = (codedeployStagingDirectory in CodeDeploy).value
      streams.value.log.info(s"Staging deployment in ${deployment}...")
      stageRevision(
        deployment = deployment,
        name = (name in CodeDeploy).value,
        version = (version in CodeDeploy).value,
        content = (codedeployContentMappings in CodeDeploy).value,
        scripts = (codedeployScriptMappings in CodeDeploy).value
      )
    },
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

  private val ContentPrefix = "content"
  private val ScriptsPrefix = "scripts"

  private def deploy(
    codeDeployClient: AmazonCodeDeployClient,
    name: String,
    s3Bucket: String,
    version: String,
    groupName: String,
    ignoreApplicationStopFailures: Boolean,
    log: Logger
  ): CreateDeploymentResult = {
    val s3Loc = s3Location(s3Bucket, name, version)
    log.info(s"Creating deployment using revision at ${s3Loc}")
    if (ignoreApplicationStopFailures) {
      log.warn(s"This deployment will ignore ApplicationStop failures.")
    }
    val result = codeDeployClient.createDeployment(
      new CreateDeploymentRequest()
        .withApplicationName(name)
        .withDeploymentGroupName(groupName)
        .withRevision(new RevisionLocation()
        .withRevisionType(RevisionLocationType.S3)
        .withS3Location(s3Loc))
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
    content: Seq[CodeDeployContentMapping],
    scripts: Seq[CodeDeployScriptMapping]
  ): Unit = {

    IO.delete(deployment)
    deployment.mkdirs

    IO.copy(content.map { content =>
      content.localSource -> deployment / ContentPrefix / content.destination
    })

    IO.copy(scripts.map { script =>
      script.source -> deployment / ScriptsPrefix / script.location
    })

    IO.write(deployment / "appspec.yml",
      generateAppSpec(content, scripts))
  }

  private def generateAppSpec(
    content: Seq[CodeDeployContentMapping],
    scripts: Seq[CodeDeployScriptMapping]
  ): String = {
    val appspec = new StringBuilder
    Seq(
      "version: 0.0",
      "os: linux",
      "hooks:"
    ).foreach { line =>
      appspec ++= s"${line}\n"
    }

    generateAppSpecHooks(appspec, scripts)

    appspec ++= "files:\n"
    generateAppSpecFiles(appspec, content)

    appspec ++= "permissions:\n"
    generateAppSpecPermissions(appspec, content)

    appspec.result
  }

  private def generateAppSpecHooks(
    appspec: StringBuilder,
    scripts: Seq[CodeDeployScriptMapping]
  ): Unit = {
    var section: String = null
    // sort includes location so that scripts for
    // each hook will run in lexicograpic order
    scripts.sortBy(script => script.section + script.location).foreach { script =>
      if (script.section != section) {
        section = script.section
        appspec ++= s"  ${section}:\n"
      }
      appspec ++= s"""    - location: ${ScriptsPrefix}/${script.location}\n"""
      appspec ++= s"""      timeout: ${script.timeout}\n"""
      appspec ++= s"""      runas: ${script.runas}\n"""
    }
  }

  private def generateAppSpecFiles(
    appspec: StringBuilder,
    content: Seq[CodeDeployContentMapping]
  ): Unit = {
    content.foreach { content =>
      if (content.copyable) {
        // the destination specified for code deploy
        // should be the parent directory of the
        // desired full path...
        val path = content.destination
        val destination = {
          val tmp = file(path)
          val parent = tmp.getParent
          // should not happen because we are excluding directories above...
          assert(parent != null, s"unexpected mapping to root ${content}")
          parent
        }
        appspec ++= s"""  - source: ${ContentPrefix}${path}\n"""
        appspec ++= s"""    destination: ${destination}\n"""
      }
    }
  }

  private def generateAppSpecPermissions(
    appspec: StringBuilder,
    content: Seq[CodeDeployContentMapping]
  ): Unit = {
    content.foreach { content =>
      // Don't try to set permissions on directories.
      // Code deploy sets permissions recursively
      // and disallows duplicate permission settings.
      // If permissions are configured separately for
      // a parent directory and one of its child directories
      // or files, code deploy will complain of duplicate
      // permissions for the child.
      // TODO figure out an efficient way to resolve this automatically
      if (!content.isDirectory) {
        appspec ++= s"""  - object: ${content.destination}\n"""
        appspec ++= s"""    mode: "${content.mode}"\n"""
        appspec ++= s"""    owner: ${content.owner}\n"""
        appspec ++= s"""    group: ${content.group}\n"""
      }
    }
  }
}
