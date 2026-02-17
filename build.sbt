lazy val scalaVer = "3.3.7"

lazy val orgName =          "org.winlogon"
lazy val packageName =      s"$orgName.teleportassist"
lazy val mainClassName =    s"$packageName.$projectName"

lazy val projectName =      "TeleportAssist"
lazy val projectVersion =   "0.4.0"
lazy val paperApiDep =      "26.1.2.build.69-stable"
lazy val apiVersion =       "1.21"
lazy val sqliteVersion =    "3.49.1.0"
lazy val asyncCraftrVersion = "0.2.0"

ThisBuild / name :=         projectName
ThisBuild / scalaVersion := scalaVer
ThisBuild / version :=      projectVersion
ThisBuild / organization := orgName
ThisBuild / organizationName := "WinSMP"
Compile / mainClass :=      Some(mainClassName)

Compile / resourceGenerators += Def.task {
    val log = streams.value.log

    val templateFile    = (Compile / resourceDirectory).value / "paper-plugin.yml.template"
    val outputDir       = (Compile / resourceManaged).value
    IO.createDirectory(outputDir)
    val outputFile      = outputDir / "paper-plugin.yml"

    val replacements = Map(
        "VERSION"     -> version.value,
        "NAME"        -> projectName,
        "MAIN_CLASS"  -> mainClassName,
        "API_VERSION" -> apiVersion,
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

ThisBuild / publishTo := None
publish / skip := true

crossScalaVersions := Seq(scalaVer)

libraryDependencies ++= Seq(
    "io.papermc.paper" % "paper-api" % paperApiDep % Provided,
    "org.xerial" % "sqlite-jdbc" % sqliteVersion % Provided,
    "org.winlogon" % "asynccraftr" % asyncCraftrVersion % Provided
)

resolvers ++= Seq(
    "papermc-repo" at "https://repo.papermc.io/repository/maven-public/",
    "winlogon-code-releases" at "https://maven.winlogon.org/releases",
)
