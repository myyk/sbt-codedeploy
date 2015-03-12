import sbt.codedeploy.ContentMapping
import sbt.codedeploy.Keys._
import sbt.codedeploy.CodeDeployPlugin

import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

packageArchetype.java_server

enablePlugins(CodeDeployPlugin)

name in CodeDeploy := "sbt-codedeploy-conventions-example"

codedeployRegion := com.amazonaws.regions.Regions.US_WEST_2

codedeployBucket := "backoffice-codedeploy-oregon"
