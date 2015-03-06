import sbt.codedeploy.CodeDeployContentMapping
import sbt.codedeploy.CodeDeployKeys._
import sbt.codedeploy.CodeDeployPlugin

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

codedeployBucket := "gilt-direct-deployments"
