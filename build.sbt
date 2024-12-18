import sbt.*
import sbt.Keys.*
import Dependencies.*
import sbtassembly.AssemblyKeys.assembly

ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.13.12"
publishMavenStyle := true

lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    name := "audit-report-core",
    libraryDependencies ++= coreDependencies ++ Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ),
    Antlr4 / antlr4Version := antlrVersion,
    Antlr4 / antlr4PackageName := Some("com.ncl.parser.pf.generated"),
    Antlr4 / antlr4GenListener := false,
    Antlr4 / antlr4GenVisitor := true
  )
  .enablePlugins(Antlr4Plugin)

//lazy val auditReportPlugin = (project in file("sbt-plugin"))
//  .enablePlugins(SbtPlugin)
//  .dependsOn(core)
//  .settings(
//    commonSettings,
//    scalaVersion := "2.12.20",
//    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.19"),
//    name := "sbt-audit-report-plugin",
//    sbtPlugin := true,
//    libraryDependencies ++= pluginDependencies
//  )

lazy val auditReportCli = (project in file("cli"))
  .dependsOn(core)
  .settings(
    commonSettings,
    name := "audit-report-cli",
    Compile / mainClass := Some("com.ncl.audit.cli.AuditReportCLI"),
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.1.0",
      "me.tongfei" % "progressbar" % "0.10.1",
      "com.lihaoyi" %% "fansi" % "0.5.0",
      "com.typesafe" % "config" % "1.4.3"
    ),
    assembly / assemblyJarName := "audit-report-cli.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  )
  .enablePlugins(AssemblyPlugin)

lazy val root = (project in file("."))
//  .aggregate(core, auditReportPlugin, auditReportCli)
  .aggregate(core, auditReportCli)
  .settings(
    name := "sbt-audit-report",
    commonSettings,
    publish / skip := true
  )
