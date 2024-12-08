import sbt._

import sbt.*
import sbt.Keys.*

object Dependencies {
  val antlrVersion = "4.13.1"
  val scalaMetaVersion = "4.8.13"
  val fastparseVersion = "3.1.1"
  val scalaTestVersion = "3.2.15"
  val commonIOVersion = "2.11.0"
  val circeVersion = "0.14.10"
  val slf4jVersion = "2.0.16"
  val logbackVersion = "1.5.12"

  val coreDependencies: Seq[ModuleID] = Seq(
    "org.scalameta" %% "scalameta" % scalaMetaVersion,
    "org.antlr" % "antlr4-runtime" % antlrVersion,
    "com.lihaoyi" %% "fastparse" % fastparseVersion,
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "org.scalatest" %% "scalatest" % scalaTestVersion % Test
  )

  val pluginDependencies: Seq[ModuleID] = Seq(
    "commons-io" % "commons-io" % commonIOVersion
  )

  val commonSettings = Seq(
    organization := "com.ncl",
    publishMavenStyle := true,
    scalaVersion := "2.12.18",
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
