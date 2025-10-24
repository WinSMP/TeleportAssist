import Dependencies._
import sbtassembly.AssemblyPlugin.defaultShellScript

lazy val scalaVer = "3.3.5"
lazy val orgName = "org.winlogon"
lazy val projectVersion = "0.2.0"
lazy val projectName = "TeleportAssist"
lazy val mainClassName = s"$orgName.teleportassist.$projectName"

ThisBuild / scalaVersion := scalaVer
ThisBuild / version := s"$projectVersion-SNAPSHOT"
ThisBuild / organization := orgName
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
        assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false)
    )

// Merge strategy for avoiding conflicts in dependencies
assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
}

assembly / mainClass := Some(mainClassName)

libraryDependencies ++= Seq(
    "io.papermc.paper" % "paper-api" % "1.21.8-R0.1-SNAPSHOT" % Provided,
    "org.mockbukkit.mockbukkit" % "mockbukkit-v1.21" % "4.89.0" % Test,
    "org.junit.jupiter" % "junit-jupiter-api" % "5.9.1" % Test,
    "org.junit.jupiter" % "junit-jupiter-engine" % "5.9.1" % Test,
    "net.aichler" % "jupiter-interface" % "0.9.0" % Test
)

resolvers ++= Seq(
    "papermc-repo" at "https://repo.papermc.io/repository/maven-public/",
    // "codemc" at "https://repo.codemc.org/repository/maven-public/"
)

dependencyOverrides ++= Seq(
  "org.junit.jupiter" % "junit-jupiter-api" % "5.9.1",
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.9.1",
  "org.junit.platform" % "junit-platform-commons" % "1.9.1",
  "org.junit.platform" % "junit-platform-engine" % "1.9.1",
  "org.junit.platform" % "junit-platform-launcher" % "1.9.1"
)
