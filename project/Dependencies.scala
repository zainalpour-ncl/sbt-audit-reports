import sbt._

import sbt.*
import sbt.Keys.*

object Dependencies {
  val antlrVersion = "4.13.2"
  val scalaMetaVersion = "4.12.1"
  val fastparseVersion = "3.1.1"
  val scalaTestVersion = "3.2.19"
  val commonIOVersion = "2.11.0"
  val circeVersion = "0.14.10"
  val slf4jVersion = "2.0.16"
  val logbackVersion = "1.5.12"

  val zioVersion       = "2.1.13"
  val zioConfigVersion = "4.0.2"
  val zioLoggingVersion = "2.4.0"
  val zioProcessVersion = "0.7.2"
  val zioJsonVersion    = "0.7.3"
  val zioCliVersion     = "0.7.0"

  val coreDependencies: Seq[ModuleID] = Seq(
    "org.scalameta" %% "scalameta" % scalaMetaVersion,
    "org.antlr" % "antlr4-runtime" % antlrVersion,
    "com.lihaoyi" %% "fastparse" % fastparseVersion,
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "dev.zio" %% "zio-json" % zioJsonVersion,
    "dev.zio" %% "zio-config" % zioConfigVersion,
    "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
    "dev.zio" %% "zio-logging" % zioLoggingVersion,
    "dev.zio" %% "zio-logging-slf4j2" % zioLoggingVersion,
    "dev.zio" %% "zio-process" % zioProcessVersion,
    "dev.zio" %% "zio-cli" % zioCliVersion,
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  )

  val pluginDependencies: Seq[ModuleID] = Seq(
    "commons-io" % "commons-io" % commonIOVersion
  )

  val commonSettings = Seq(
    organization := "com.ncl",
    publishMavenStyle := true,
    scalaVersion := "2.13.15",
    scalacOptions := Seq(
      "-feature",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:postfixOps",
      "-language:experimental.macros",
      "-deprecation",
      "-unchecked",
      "-Ywarn-dead-code",
      "-Ywarn-extra-implicit",
      "-Ywarn-value-discard"
    )
  )
}
