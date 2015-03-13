import sbt.codedeploy.Keys._
import sbt.codedeploy.PermissionMapping
import sbt.codedeploy.CodeDeployPlugin

organization := "com.example"

enablePlugins(CodeDeployPlugin)

codedeployBucket := "nowhere"

codedeployPermissionMappings := Seq(
  PermissionMapping(
    objectPath = "lib",
    mode = "0400",
    owner = "root",
    group = "root",
    objectType = Some("file")
  )
)
