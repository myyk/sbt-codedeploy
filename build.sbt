releaseSettings

sonatypeSettings

ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value

name := "sbt-codedeploy"

organization := "com.github.myyk"

sbtPlugin := true

bintrayPackageLabels := Seq("aws", "cloudformation")

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-feature",
  "-unchecked"
)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

val awsSdkVersion = "1.9.40"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-codedeploy" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
)

resolvers += Resolver.url("myyk-bintray-sbt-plugins", url("https://dl.bintray.com/myyk/sbt-plugins/"))(Resolver.ivyStylePatterns)
addSbtPlugin("com.github.myyk" % "sbt-cloudformation" % "0.5.0")

homepage := Some(url("https://github.com/gilt/sbt-codedeploy"))

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

pomExtra := (
  <scm>
    <url>https://github.com/gilt/sbt-codedeploy.git</url>
    <connection>scm:git:git@github.com:gilt/sbt-codedeploy.git</connection>
  </scm>
  <developers>
    <developer>
      <id>haywood</id>
      <name>Michael Reed</name>
      <url>https://github.com/haywood</url>
    </developer>
    <developer>
      <id>myyk</id>
      <name>Myyk Seok</name>
      <url>https://github.com/myyk</url>
    </developer>
  </developers>
)

scriptedSettings
scriptedLaunchOpts += s"-Dproject.version=${version.value}"
