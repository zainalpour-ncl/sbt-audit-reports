package com.ncl.audit

import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import scala.io.Source

// scalastyle:off
object ProjectExtractorUtil {
  def extractProjectName(baseDir: File): String = {
    val buildSbt = Paths.get(baseDir.getAbsolutePath, "build.sbt")
    if (!Files.exists(buildSbt)) throw new Exception("build.sbt file not found")

    val lines = Source.fromFile(buildSbt.toFile).getLines()
    lines
      .collectFirst {
        case line if line.trim.startsWith("name :=") =>
          line.split(":= ")(1).trim.replace("\"", "")
      }
      .getOrElse(throw new Exception("Project name not found in build.sbt"))
  }

  def extractRepository(baseDir: File): String = {
    val gitConfig = Paths.get(baseDir.getAbsolutePath, ".git", "config")
    if (!Files.exists(gitConfig)) throw new Exception(".git/config file not found")

    val lines = Source.fromFile(gitConfig.toFile).getLines()
    lines
      .collectFirst {
        case line if line.trim.startsWith("url =") =>
          line.split("url =")(1).trim
      }
      .getOrElse(throw new Exception("Repository URL not found in .git/config"))
  }

  def writeProjectModelToJson(project: ProjectModel, outputPath: File): Unit = {
    val printer = Printer.spaces2.copy(dropNullValues = true)
    val json = printer.print(project.asJson)
    Files.write(Paths.get(outputPath.getAbsolutePath), json.getBytes(StandardCharsets.UTF_8))
  }
}
