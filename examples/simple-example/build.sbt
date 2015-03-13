import sbt.codedeploy.Keys._
import sbt.codedeploy.CodeDeployPlugin

enablePlugins(CodeDeployPlugin)

codedeployBucket := "backoffice-codedeploy-oregon"

name in CodeDeploy := "sbt-codedeploy-simple-example"

codedeployRegion := com.amazonaws.regions.Regions.US_WEST_2

codedeployPermissionMappings := Seq(
  sbt.codedeploy.PermissionMapping(
    objectPath = "bin/simple-example",
    mode = "0755",
    owner = "root",
    group = "root"
  )
)
