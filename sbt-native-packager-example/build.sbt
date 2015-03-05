import sbt.codedeploy.CodeDeployContentMapping
import sbt.codedeploy.CodeDeployKeys._
import sbt.codedeploy.CodeDeployPlugin

import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

codedeployBucket := "gilt-direct-deployments"

codedeployContentMappings := {
  val symlinks = linuxPackageSymlinks.value
  linuxPackageMappings.value.flatMap { mapping =>
    val fileData = mapping.fileData
    mapping.mappings.map { case (from, to) =>
      CodeDeployContentMapping(
        localSource = from,
        destination = to,
        mode = fileData.permissions,
        owner = fileData.user,
        group = fileData.group,
        symlinkTarget = symlinks.collectFirst {
          case link if link.link == to => link.link
        }
      )
    }
  }
}
