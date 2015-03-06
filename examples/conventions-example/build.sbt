import sbt.codedeploy.ContentMapping
import sbt.codedeploy.Keys._
import sbt.codedeploy.CodeDeployPlugin

import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

name in CodeDeploy := "svc-payment-manager"

codedeployRegion := com.amazonaws.regions.Regions.US_WEST_2

codedeployBucket := "svc-payment-manager-codedeploy"

codedeployContentMappings := {
  val buffer = collection.mutable.ArrayBuffer.empty[ContentMapping]

  linuxPackageMappings.value.foreach { mapping =>
    val fileData = mapping.fileData
    mapping.mappings.foreach { case (from, to) =>
      buffer += ContentMapping(
        file = from,
        source = s"content/${to}",
        destination = to
      )
    }
  }

  buffer
}
