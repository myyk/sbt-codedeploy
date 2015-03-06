import sbt.codedeploy.CodeDeployContentMapping
import sbt.codedeploy.CodeDeployKeys._
import sbt.codedeploy.CodeDeployPlugin

import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

name := "svc-payment-manager"

codedeployBucket := "svc-payment-manager-codedeploy"

codedeployContentMappings := {
  val buffer = collection.mutable.ArrayBuffer.empty[CodeDeployContentMapping]

  linuxPackageMappings.value.foreach { mapping =>
    val fileData = mapping.fileData
    mapping.mappings.foreach { case (from, to) =>
      buffer += CodeDeployContentMapping(
        localSource = from,
        destination = to,
        mode = fileData.permissions,
        owner = fileData.user,
        group = fileData.group,
        symlinkSource = None
      )
    }
  }

  linuxPackageSymlinks.value.foreach { link =>
    buffer += CodeDeployContentMapping(
      localSource = file("/dev/null"),
      destination = link.link,
      mode = "0600",
      owner = "root",
      group = "root",
      symlinkSource = Some(link.destination)
    )
  }
  buffer
}
