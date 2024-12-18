package com.ncl.audit.cli.program

import com.ncl.audit.cli.services._
import zio._
import zio.logging._
import zio.logging.backend.SLF4J

// scalastyle:off
object Layers {
  val loggingLayer: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val repositoryProcessingLayer: ULayer[RepositoryProcessingService] =
    FileSystemService.live ++
      ProtobufService.live ++
      ScalaSourceAnalyzerService.live ++
      RoutesParserService.live ++
      SamlConfigurationService.live >>> RepositoryProcessingService.live

  val appLayer: ULayer[
    ConfigurationService
      with DependencyCheckerService
      with RepositoryService
      with DependencyResolverService
      with ReportGenerationService
      with RepositoryProcessingService
      with Unit
  ] =
    ZLayer.make[
      ConfigurationService
        with DependencyCheckerService
        with RepositoryService
        with DependencyResolverService
        with ReportGenerationService
        with RepositoryProcessingService
        with Unit
    ](
      loggingLayer,
      ConfigurationService.live,
      DependencyCheckerService.live,
      RepositoryService.live,
      DependencyResolverService.live,
      ReportGenerationService.live,
      repositoryProcessingLayer
    )
}

