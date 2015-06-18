import sbt.codedeploy.PermissionMapping

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
