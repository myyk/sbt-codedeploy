import sbt.codedeploy.ContentMapping
import sbt.codedeploy.Keys._
import sbt.codedeploy.CodeDeployPlugin

enablePlugins(CodeDeployPlugin)

name in CodeDeploy := "sbt-codedeploy-conventions-example"

codedeployRegion := com.amazonaws.regions.Regions.US_WEST_2

codedeployBucket := "backoffice-codedeploy-oregon"
