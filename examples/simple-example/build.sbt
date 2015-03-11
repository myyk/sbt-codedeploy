import sbt.codedeploy.Keys._
import sbt.codedeploy.CodeDeployPlugin

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

codedeployBucket := "gilt-direct-deployments"

name in CodeDeploy := "sbt-codedeploy-simple-example"

codedeployPermissionMappings := Seq(
  sbt.codedeploy.PermissionMapping(
    objectPath = "bin/simple-example",
    mode = "0755",
    owner = "root",
    group = "root"
  )
)
