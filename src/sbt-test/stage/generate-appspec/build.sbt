import sbt.codedeploy.Keys._
import sbt.codedeploy.PermissionMapping
import sbt.codedeploy.CodeDeployPlugin

enablePlugins(CodeDeployPlugin)

codedeployBucket := "nowhere"

codedeployPermissionMappings := Seq(
  PermissionMapping(
    objectPath = "lib",
    mode = "0600",
    owner = "root",
    group = "root"
  )
)
