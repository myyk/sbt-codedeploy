import sbt.codedeploy.Keys._
import sbt.codedeploy.CodeDeployPlugin

enablePlugins(CodeDeployPlugin)

codedeployBucket := "nowhere"
