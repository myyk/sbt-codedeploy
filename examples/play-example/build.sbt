import sbt.codedeploy._
import sbt.codedeploy.Keys._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.NativePackagerKeys._

name := "play-example"

version := "1.0-SNAPSHOT"

enablePlugins(PlayScala, CodeDeployPlugin)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws
)

name in CodeDeploy := "sbt-codedeploy-play-example"
codedeployRegion := com.amazonaws.regions.Regions.US_WEST_2
codedeployBucket := "backoffice-codedeploy-oregon"

import com.typesafe.sbt.packager.archetypes.ServerLoader
serverLoading := ServerLoader.Upstart

packageArchetype.java_server

codedeployIgnoreApplicationStopFailures := true

codedeployContentMappings := {
  val relativePath = "/etc/init/play-example.conf"
  val startConf = (sourceDirectory in CodeDeploy).value / "content" / relativePath
  val start = ContentMapping(
    file = startConf,
    source = relativePath,
    destination = "/etc/init"
  )

  val symlinks = linuxPackageSymlinks.value.map(_.link).toSet
  val buffer = collection.mutable.ListBuffer.empty[ContentMapping]
  for {
    mapping <- linuxPackageMappings.value
    (from, to) <- mapping.mappings
    if (!from.isDirectory && !symlinks(to))
  } {
    buffer += ContentMapping(
      file = from,
      source = to,
      destination = new File(to).getParent
    )
  }
  buffer += start
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
