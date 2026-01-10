lazy val scalaVer = "3.3.5"

// Identifiers used to construct fully-qualified class names
lazy val orgName = "org.winlogon"
lazy val packageName = s"$orgName.teleportassist"
lazy val mainClassName = s"$packageName.$projectName"

// Project metadata used for builds, publishing, and resource generation
lazy val projectName = "TeleportAssist"
lazy val projectVersion = "0.3.0"
lazy val minecraftVersion = "1.21.6"

ThisBuild / name := projectName
ThisBuild / scalaVersion := scalaVer
ThisBuild / version := projectVersion
ThisBuild / organization := orgName
ThisBuild / organizationName := "WinSMP"
Compile / mainClass := Some(mainClassName)

Compile / resourceGenerators += Def.task {
    val log = streams.value.log

    val templateFile = (Compile / resourceDirectory).value / "paper-plugin.yml.template"
    val outputDir    = (Compile / resourceManaged).value
    IO.createDirectory(outputDir)
    val outputFile   = outputDir / "paper-plugin.yml"

    val replacements = Map(
        "VERSION"     -> version.value,
        "NAME"        -> name.value,
        "MAIN_CLASS"  -> mainClassName,
        "API_VERSION" -> minecraftVersion,
        "PACKAGE"     -> packageName
    )

    val content = IO.read(templateFile)
    val replaced = replacements.foldLeft(content) {
        case (acc, (key, value)) => acc.replace(s"$${$key}", value)
    }

    IO.write(outputFile, replaced)
    log.info(s"Processed paper-plugin.yml @ $outputFile")

    Seq(outputFile)
}.taskValue

// GitHub CI
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))
ThisBuild / publishTo := None
publish / skip := true

crossScalaVersions := Seq(scalaVer)

lazy val root = (project in file("."))
    .settings(
        assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false)
    )

// Merge strategy for avoiding conflicts in dependencies
assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
}

assembly / mainClass := Some(mainClassName)

libraryDependencies ++= Seq(
    "io.papermc.paper" % "paper-api" % s"$minecraftVersion-R0.1-SNAPSHOT" % Provided,
)

resolvers ++= Seq(
    "papermc-repo" at "https://repo.papermc.io/repository/maven-public/",
)
