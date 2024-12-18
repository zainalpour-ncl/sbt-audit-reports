package com.ncl.audit.cli.services

import com.ncl.audit.MethodCall
import com.ncl.audit.ServiceCall
import zio._

import java.io.File
import scala.io.Source
import scala.util.matching.Regex

trait ScalaSourceAnalyzerService {
  def analyzeScalaFiles(scalaFiles: List[File]): Task[Set[ServiceCall]]
}

final case class ScalaSourceAnalyzerServiceImpl() extends ScalaSourceAnalyzerService {

  override def analyzeScalaFiles(scalaFiles: List[File]): Task[Set[ServiceCall]] =
    ZIO.foreachPar(scalaFiles)(analyzeSingleFile).map(_.flatten.toSet)

  private def analyzeSingleFile(file: File): Task[Set[ServiceCall]] = {
    val serviceCallPattern: Regex = """(\w+)\.(\w+)\s*\(.*\)""".r

    withResource(Source.fromFile(file)) { source =>
      ZIO.attemptBlockingIO {
        val lines = source.getLines().mkString("\n")
        serviceCallPattern
          .findAllMatchIn(lines)
          .map { callMatch =>
            val serviceName = callMatch.group(1) // Extract service name
            val methodName = callMatch.group(2) // Extract method name

            ServiceCall(
              serviceName = serviceName,
              calledMethods = Set(MethodCall(methodName = methodName)),
              calledIn = Some(file.getAbsolutePath)
            )
          }
          .toSet
      }
    }.mapError(e => new Exception(s"Failed to analyze Scala file: ${file.getAbsolutePath}", e))
  }
}

object ScalaSourceAnalyzerService {
  val live: ULayer[ScalaSourceAnalyzerService] = ZLayer.fromFunction(ScalaSourceAnalyzerServiceImpl.apply _)

  def analyzeScalaFiles(scalaFiles: List[File]): RIO[ScalaSourceAnalyzerService, Set[ServiceCall]] =
    ZIO.serviceWithZIO[ScalaSourceAnalyzerService](_.analyzeScalaFiles(scalaFiles))
}
