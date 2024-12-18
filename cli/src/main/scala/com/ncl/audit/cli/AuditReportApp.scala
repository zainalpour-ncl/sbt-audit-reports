package com.ncl.audit.cli

import com.ncl.audit.cli.program.Layers
import com.ncl.audit.cli.services._
import zio._

import java.io.File

object AuditReportApp extends ZIOAppDefault {

  private val program = for {
    args <- ZIOAppArgs.getArgs
    cliConfig <- ConfigurationService.parseCliArguments(args.toList)
    appConfig <- ConfigurationService.loadConfig(cliConfig)

    _ <- DependencyCheckerService.checkDependencies(Seq("gh", "jq"))

    baseFolder = cliConfig.inputFolder.getOrElse(new File("/tmp/cloned_repos"))
    _ <- RepositoryService.cloneRepositories(appConfig, baseFolder)

    projectModels <- RepositoryProcessingService.processRepositories(baseFolder, appConfig.products)

    allServices = projectModels.flatMap(_.services).toSet
    resolvedModels <- DependencyResolverService.resolveDependencies(projectModels, allServices)

    outputFolder <- ZIO.succeed(cliConfig.outputFolder)
    _ <- ReportGenerationService.generateReports(resolvedModels, outputFolder)
  } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program
      .provideSomeLayer[ZIOAppArgs with Scope](Layers.appLayer)
      .tapError(err => ZIO.logError(s"Application failed: ${err.getMessage}"))
      .exitCode
}
