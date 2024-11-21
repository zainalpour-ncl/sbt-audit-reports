import sbt.*
import sbt.Keys.*
import Dependencies.*

ThisBuild / version := "0.1.0"
publishMavenStyle := true

sbtPlugin := true

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

lazy val auditReportPlugin = (project in file("sbt-plugin"))
  .enablePlugins(SbtPlugin)
  .dependsOn(core)
  .settings(
    commonSettings,
    addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.19"),
    name := "sbt-audit-report-plugin",
    libraryDependencies ++= pluginDependencies,
  )

lazy val root = (project in file("."))
  .aggregate(core, auditReportPlugin)
  .settings(
    name := "sbt-audit-report",
    commonSettings,
    publish / skip := true
  )
