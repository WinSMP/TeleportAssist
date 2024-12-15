import Dependencies._

ThisBuild / scalaVersion     := "3.3.4"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "org.winlogon"
ThisBuild / organizationName := "winlogon"
Compile / mainClass := Some("org.winlogon.TpaPlugin")

// GitHub CI
ThisBuild / githubWorkflowJavaVersions += JavaSpec.temurin("21")
ThisBuild / publishTo := None
publish / skip := true

crossScalaVersions := Seq("3.3.4")

lazy val root = (project in file("."))
  .settings(
    name := "tpa-plugin",
    libraryDependencies += munit % Test
  )

// Merge strategy for avoiding conflicts in dependencies
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assembly / mainClass := Some("org.winlogon.TpaPlugin")

libraryDependencies ++= Seq(
  "io.papermc.paper" % "paper-api" % "1.21.1-R0.1-SNAPSHOT",
)

resolvers += "papermc-repo" at "https://repo.papermc.io/repository/maven-public/"
// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
