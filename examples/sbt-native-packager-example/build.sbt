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

codedeployContentMappings ++= {
  makeBashScript.value.toSeq.map { script =>
    val bin = s"bin"
    ContentMapping(
      file = script,
      source = s"${bin}/${name.value}",
      destination = bin
    )
  }
}

codedeployContentMappings := {
  codedeployContentMappings.value.map { mapping =>
    mapping.copy(
      destination = s"/usr/share/${name.value}/${mapping.destination}"
    )
  }
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
