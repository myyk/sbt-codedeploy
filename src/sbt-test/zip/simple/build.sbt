import sbt.codedeploy.CodeDeployPlugin

import com.github.tptodorov.sbt.cloudformation.CloudFormation
import com.github.tptodorov.sbt.cloudformation.Import.Keys._
import com.github.tptodorov.sbt.cloudformation.Import.Configurations._

CloudFormation.defaultSettings
stackRegion := "US_EAST_1"

codedeployBucket := "nowhere"
