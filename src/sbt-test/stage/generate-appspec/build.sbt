import sbt.codedeploy.PermissionMapping

import com.github.tptodorov.sbt.cloudformation.CloudFormation
import com.github.tptodorov.sbt.cloudformation.Import.Keys._
import com.github.tptodorov.sbt.cloudformation.Import.Configurations._

CloudFormation.defaultSettings

stackRegion := "US_EAST_1"

organization := "com.example"

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
