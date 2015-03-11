import sbt.codedeploy.CodeDeployPlugin
import sbt.codedeploy.ContentMapping
import sbt.codedeploy.PermissionMapping
import sbt.codedeploy.Keys._

import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.NativePackagerKeys._

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

codedeployBucket := "gilt-direct-deployments"

name in CodeDeploy := "sbt-codedeploy-sbt-native-packager-example"

codedeployContentMappings := {
  val buffer = collection.mutable.ArrayBuffer.empty[ContentMapping]
  val symlinks = linuxPackageSymlinks.value.map(_.link).toSet
  linuxPackageMappings.value.foreach { mapping =>
    mapping.mappings.foreach { case (from, to) =>
      if (!from.isDirectory && !symlinks(to)) {
        buffer += ContentMapping(
          file = from,
          source = to,
          destination = new File(to).getParent
        )
      }
    }
  }
  buffer
}

codedeployPermissionMappings := {
  val name = sbt.Keys.name.value

  Seq(
    PermissionMapping(
      objectPath = s"/usr/share/${name}/bin/${name}",
      mode = "0755",
      owner = "root",
      group = "root"
    )
  )
}
