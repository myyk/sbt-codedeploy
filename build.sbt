releaseSettings

name := "sbt-codedeploy"

organization := "com.gilt"

sbtPlugin := true

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-feature",
  "-unchecked"
)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-codedeploy" % "1.9.17",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.9.17"
)
