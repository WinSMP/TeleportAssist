import Dependencies._

lazy val scalaVer = "3.3.5"
lazy val orgName = "org.winlogon"
lazy val projectVersion = "0.2.0"
lazy val projectName = "TeleportAssist"
lazy val mainClassName = s"$orgName.teleportassist.$projectName"

ThisBuild / scalaVersion     := scalaVer
ThisBuild / version          := s"$projectVersion-SNAPSHOT"
ThisBuild / organization     := orgName
ThisBuild / organizationName := "winlogon"
Compile / mainClass := Some(mainClassName)

// GitHub CI
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))
ThisBuild / publishTo := None
publish / skip := true

crossScalaVersions := Seq(scalaVer)

lazy val root = (project in file("."))
  .settings(
    name := projectName,
    libraryDependencies += munit % Test
  )

// Merge strategy for avoiding conflicts in dependencies
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

assembly / mainClass := Some(mainClassName)

libraryDependencies ++= Seq(
  "io.papermc.paper" % "paper-api" % "1.21.4-R0.1-SNAPSHOT" % Provided,
  "dev.jorel" % "commandapi-bukkit-core" % "9.7.0" % Provided,
)

resolvers ++= Seq(
  "papermc-repo" at "https://repo.papermc.io/repository/maven-public/",
  "codemc" at "https://repo.codemc.org/repository/maven-public/",
)
