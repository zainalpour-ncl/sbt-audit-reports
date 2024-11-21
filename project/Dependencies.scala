import sbt._

import sbt.*
import sbt.Keys.*

object Dependencies {
  val antlrVersion = "4.13.1"
  val http4sVersion = "1.0-234-d1a2b53"
  val circeVersion = "0.13.0"
  val scalaMetaVersion = "4.8.13"
  val catsVersion = "2.10.0"
  val scalaTestVersion = "3.2.15"
  val commonIOVersion = "2.11.0"

  val coreDependencies: Seq[ModuleID] = Seq(
    "org.scalameta" %% "scalameta" % scalaMetaVersion,
    "org.antlr" % "antlr4-runtime" % antlrVersion,
    "org.eclipse.jgit" % "org.eclipse.jgit" % "6.8.0.202311291450-r",
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.typelevel" %% "cats-core" % catsVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
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
