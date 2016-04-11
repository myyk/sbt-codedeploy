releaseSettings

sonatypeSettings

ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value

name := "sbt-codedeploy"

organization := "com.gilt"

sbtPlugin := true

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-feature",
  "-unchecked"
)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

val awsSdkVersion = "1.10.16"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-codedeploy" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
)

addSbtPlugin("com.github.tptodorov" % "sbt-cloudformation" % "0.4.0")

homepage := Some(url("https://github.com/gilt/sbt-codedeploy"))

licenses := Seq("MIT" -> url("https://raw.githubusercontent.com/gilt/sbt-codedeploy/master/LICENSE"))

publishTo := Some {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    "snapshots" at nexus + "content/repositories/snapshots"
  else
    "releases" at nexus + "service/local/staging/deploy/maven2"
}

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
