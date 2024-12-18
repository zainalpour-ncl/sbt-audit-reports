package com.ncl.audit.cli.services

import com.ncl.audit.RestEndpoint
import zio._
import scala.io.Source
import java.io.File
import scala.util.matching.Regex

trait RoutesParserService {
  def parseRoutes(routesFiles: List[File]): Task[Set[RestEndpoint]]
}

final case class RoutesParserServiceImpl() extends RoutesParserService {

  override def parseRoutes(routesFiles: List[File]): Task[Set[RestEndpoint]] =
    ZIO.foreachPar(routesFiles)(parseSingleRoutesFile).map(_.flatten.toSet)

  private def parseSingleRoutesFile(file: File): Task[Set[RestEndpoint]] = {
    val routePattern: Regex = """^(GET|POST|PUT|DELETE|PATCH|OPTIONS|HEAD)\s+(\S+)\s+(\S+)\s*(.*)$""".r

    withResource(Source.fromFile(file)) { source =>
      ZIO.attemptBlockingIO {
        source
          .getLines()
          .flatMap { line =>
            routePattern.findFirstMatchIn(line).map { m =>
              RestEndpoint(
                method = m.group(1),
                path = m.group(2),
                controller = m.group(3),
                inputParameters = Option(m.group(4)).filter(_.nonEmpty)
              )
            }
          }
          .toSet
      }
    }.mapError(e => new Exception(s"Failed to parse routes file: ${file.getAbsolutePath}", e))
  }
}

object RoutesParserService {
  val live: ULayer[RoutesParserService] = ZLayer.fromFunction(RoutesParserServiceImpl.apply _)

  def parseRoutes(routesFiles: List[File]): RIO[RoutesParserService, Set[RestEndpoint]] =
    ZIO.serviceWithZIO[RoutesParserService](_.parseRoutes(routesFiles))
}
