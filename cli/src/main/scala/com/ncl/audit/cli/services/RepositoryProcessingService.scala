package com.ncl.audit.cli.services

import com.ncl.audit.ProjectDependency
import com.ncl.audit.ProjectModel
import com.ncl.audit.RestEndpoint
import com.ncl.audit.SamlConfiguration
import com.ncl.audit.Service
import com.ncl.audit.ServiceCall
import com.ncl.audit.cli.model.ProductConfig
import zio._

import java.io.File

trait RepositoryProcessingService {
  def processRepositories(baseFolder: File, products: List[ProductConfig]): Task[Seq[ProjectModel]]
}

case class ParsingResults(
  services: Set[Service],
  serviceCalls: Set[ServiceCall],
  restEndpoints: List[RestEndpoint],
  samlConfigurations: List[SamlConfiguration]
)

final case class RepositoryProcessingServiceImpl(
  fileSystemService: FileSystemService,
  protobufService: ProtobufService,
  scalaSourceAnalyzerService: ScalaSourceAnalyzerService,
  routesParserService: RoutesParserService,
  samlConfigurationService: SamlConfigurationService
) extends RepositoryProcessingService {

  override def processRepositories(baseFolder: File, products: List[ProductConfig]): Task[Seq[ProjectModel]] =
    ZIO.logInfo("Starting repository processing...") *>
      ZIO
        .foreachPar(products) { productConfig =>
          processProductRepositories(baseFolder, productConfig)
        }
        .map(_.flatten)
        .tapError(e => ZIO.logError(s"Failed to process repositories: ${e.getMessage}"))
        .zipLeft(ZIO.logInfo("Repository processing completed."))

  private def processProductRepositories(baseFolder: File, productConfig: ProductConfig): Task[Seq[ProjectModel]] =
    ZIO.foreachPar(productConfig.projects) { projectName =>
      processSingleRepository(baseFolder, productConfig.product, projectName)
        .tapBoth(
          err => ZIO.logError(s"Error processing repository $projectName: ${err.getMessage}"),
          _ => ZIO.logInfo(s"Successfully processed repository: $projectName")
        )
    }

  private def processSingleRepository(baseFolder: File, product: String, projectName: String): Task[ProjectModel] = {
    val repoFolder = new File(baseFolder, projectName)

    for {
      _ <- ZIO.logInfo(s"Processing repository: $projectName")

      fibers <- ZIO.forkAll(
        Seq(
          fileSystemService.findFilesWithExtension(repoFolder, "proto"),
          fileSystemService.findFilesWithExtension(repoFolder, "scala"),
          fileSystemService.findRoutesConfFiles(repoFolder),
          fileSystemService.findSamlConfFiles(repoFolder)
        )
      )

      results <- fibers.join
      List(protoFiles, scalaFiles, routesFiles, samlFiles) = results

      parsingResults <- parseFiles(protoFiles, scalaFiles, routesFiles, samlFiles)

    } yield ProjectModel(
      name = projectName,
      repository = repoFolder.getAbsolutePath,
      product = product,
      services = parsingResults.services,
      dependencies = Set(ProjectDependency(parsingResults.serviceCalls)),
      restEndpoints = parsingResults.restEndpoints.toSet, // Convert List to Set
      samlConfigurations = parsingResults.samlConfigurations.toSet // Convert List to Set
    )
  }

  private def parseFiles(
    protoFiles: List[File],
    scalaFiles: List[File],
    routesFiles: List[File],
    samlFiles: List[File]
  ): Task[ParsingResults] =
    for {
      servicesFiber <- protobufService.parseProtoFiles(protoFiles).fork
      serviceCallsFiber <- scalaSourceAnalyzerService.analyzeScalaFiles(scalaFiles).fork
      restEndpointsFiber <- routesParserService.parseRoutes(routesFiles).fork
      samlConfigurationsFiber <- samlConfigurationService.parseSamlConfigs(samlFiles).fork

      // Wait for all fibers and construct the ParsingResults
      services <- servicesFiber.join
      serviceCalls <- serviceCallsFiber.join
      restEndpoints <- restEndpointsFiber.join
      samlConfigurations <- samlConfigurationsFiber.join

    } yield ParsingResults(
      services = services,
      serviceCalls = serviceCalls,
      restEndpoints = restEndpoints.toList,
      samlConfigurations = samlConfigurations.toList
    )
}

object RepositoryProcessingService {
  // scalastyle:off
  val live: ZLayer[
    FileSystemService
      with ProtobufService
      with ScalaSourceAnalyzerService
      with RoutesParserService
      with SamlConfigurationService,
    Nothing,
    RepositoryProcessingService
  ] =
    ZLayer.fromFunction(RepositoryProcessingServiceImpl.apply _)

  def processRepositories(
    baseFolder: File,
    products: List[ProductConfig]
  ): RIO[RepositoryProcessingService, Seq[ProjectModel]] =
    ZIO.serviceWithZIO[RepositoryProcessingService](_.processRepositories(baseFolder, products))
}
