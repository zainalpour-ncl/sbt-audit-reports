package com.ncl.audit.cli

import com.ncl.audit.RoutesParser
import com.ncl.audit._
import fansi.Color._
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.slf4j.LoggerFactory
import scopt.OParser

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import scala.collection.mutable
import scala.io.Source
import scala.sys.process._
import scala.util.Try

object ProtobufAuditCLI extends App {

  private val logger = LoggerFactory.getLogger(ProtobufAuditCLI.getClass)

  case class Config(
    inputFolder: Option[File] = None,
    githubUrl: String = "https://github.com",
    organization: String = "norwegian-cruise-line",
    outputFolder: File = new File("output"),
    configFile: Option[File] = None
  )

  case class AppConfig(product: String = "ncl", projects: List[String])

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("protobuf-audit-cli"),
      head("Protobuf Audit CLI", "1.0"),
      opt[File]('i', "input")
        .optional()
        .valueName("<folder>")
        .action((x, c) => c.copy(inputFolder = Some(x)))
        .text("Folder containing the cloned repositories. Defaults to '/tmp/cloned_repos'."),
      opt[String]('g', "githubUrl")
        .optional()
        .valueName("<url>")
        .action((x, c) => c.copy(githubUrl = x))
        .text("GitHub server URL. Defaults to 'https://github.com'."),
      opt[String]('o', "organization")
        .optional()
        .valueName("<organization>")
        .action((x, c) => c.copy(organization = x))
        .text("Organization name for cloning repositories. Defaults to 'norwegian-cruise-line'."),
      opt[File]('d', "output")
        .optional()
        .valueName("<folder>")
        .action((x, c) => c.copy(outputFolder = x))
        .text("Folder for storing output reports. Defaults to './output'."),
      opt[File]('c', "config")
        .optional()
        .valueName("<file>")
        .action((x, c) => c.copy(configFile = Some(x)))
        .text("Configuration file specifying product and projects.")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      if (!checkDependencies(Seq("gh", "jq"))) {
        logger.error(Red("Required applications are missing. Please ensure both 'gh' and 'jq' are installed.").render)
        sys.exit(1)
      }

      logger.info(Cyan("Loading configuration...").render)
      val appConfig = config.configFile.map(loadAppConfig)
      val baseFolder = config.inputFolder.getOrElse(new File("/tmp/cloned_repos"))

      logger.info(Magenta("Cloning repositories if necessary...").render)
      val clonedRepos = cloneRepositories(config.githubUrl, config.organization, baseFolder, appConfig)
      logger.info(Green("Cloning complete.").render)

      logger.info(Magenta("Processing repositories...").render)
      val projectModels = processRepositories(config, baseFolder, appConfig)
      logger.info(Green("Repository processing complete.").render)

      logger.info(Magenta("Resolving project dependencies...").render)
      val resolvedModels = resolveProjectDependencies(projectModels)
      logger.info(Green("Dependency resolution complete.").render)

      logger.info(Magenta("Writing models to disk...").render)
      writeModelsToDisk(resolvedModels, config.outputFolder, appConfig)
      logger.info(Green("All models have been successfully written.").render)

    case _ =>
      logger.error(Red("Invalid arguments or help requested.").render)
  }

  private def checkDependencies(dependencies: Seq[String]): Boolean =
    dependencies.forall { app =>
      val result = s"which $app".!
      if (result != 0) logger.error(Red(s"Application '$app' is not installed or not found in PATH.").render)
      result == 0
    }

  private def loadAppConfig(file: File): AppConfig = {
    val content = Try(Source.fromFile(file).mkString).getOrElse {
      logger.error(Red(s"Failed to read config file: ${file.getAbsolutePath}").render)
      sys.exit(1)
    }
    import io.circe.generic.auto._
    import io.circe.parser._
    decode[AppConfig](content) match {
      case Right(config) => config
      case Left(error) =>
        logger.error(Red(s"Failed to parse config file: $error").render)
        sys.exit(1)
    }
  }

  private def cloneRepositories(
    githubUrl: String,
    organization: String,
    baseFolder: File,
    appConfig: Option[AppConfig]
  ): Seq[String] = {
    if (!baseFolder.exists()) baseFolder.mkdirs()

    val repos = appConfig match {
      case Some(config) => config.projects
      case None =>
        logger.info(Cyan(s"Listing repositories for organization: $organization").render)
        val listCmd = s"gh repo list $organization --json name | jq -r '.[].name'"
        val repoList = listCmd.!!.trim.split("\n").toSeq
        logger.info(Cyan(s"Found ${repoList.size} repositories.").render)
        repoList
    }

    if (repos.nonEmpty) {
      logger.info(Yellow(s"Cloning up to ${repos.size} repositories. This may take a while...").render)
    } else {
      logger.warn(Yellow("No repositories found to clone.").render)
    }

    val pb = new ProgressBarBuilder()
      .setTaskName("Cloning Repos")
      .setInitialMax(repos.size)
      .setStyle(ProgressBarStyle.UNICODE_BLOCK)
      .build()

    try
      repos.foreach { repo =>
        val repoPath = Paths.get(baseFolder.getAbsolutePath, repo).toFile
        if (!repoPath.exists()) {
          val cloneCommand = s"git clone $githubUrl/$organization/$repo.git ${repoPath.getAbsolutePath}"
          cloneCommand.!
        }
        pb.step()
      }
    finally
      pb.close()

    repos
  }

  private def processRepositories(
    config: Config,
    inputFolder: File,
    appConfig: Option[AppConfig]
  ): Seq[ProjectModel] = {
    val projects = appConfig match {
      case Some(AppConfig(product, pjs)) =>
        pjs.map(p => (product, p))
      case None =>
        val dirs = inputFolder.listFiles().filter(_.isDirectory).map(d => ("ncl", d.getName)).toSeq
        dirs
    }

    if (projects.nonEmpty) {
      logger.info(Yellow(s"Processing ${projects.size} repositories...").render)
    } else {
      logger.warn(Yellow("No repositories found to process.").render)
    }

    val pb = new ProgressBarBuilder()
      .setTaskName("Processing Repos")
      .setInitialMax(projects.size)
      .setStyle(ProgressBarStyle.UNICODE_BLOCK)
      .build()

    val models =
      try
        projects.flatMap { case (product, projectName) =>
          val repoFolder = new File(inputFolder, projectName)
          if (repoFolder.exists() && repoFolder.isDirectory) {
            val model = processSingleRepository(repoFolder, config, product, projectName)
            pb.step()
            Some(model)
          } else {
            pb.step()
            None
          }
        }
      finally
        pb.close()

    models
  }

  private def processSingleRepository(
    repoFolder: File,
    config: Config,
    product: String,
    projectName: String
  ): ProjectModel = {
    logger.debug(Blue(s"Processing repository: ${repoFolder.getName}").render)

    val protoFiles = findFilesWithExtension(repoFolder, "proto")
    val scalaFiles = findFilesWithExtension(repoFolder, "scala")
    val routesConfFiles = findRoutesConfFiles(repoFolder)

    // Process gRPC services from .proto files
    val allServices = protoFiles.flatMap(file => ProtobufParserUtil.parseFile(file.toString)).toSet

    // Process service calls from .scala files
    val enrichedServiceCalls = scalaFiles.flatMap { file =>
      val sourceCode = Files.readString(file.toPath, StandardCharsets.UTF_8)
      InjectedServiceAnalyzer.analyzeServiceCalls(sourceCode, file.getName)
    }.toSet

    // Process REST endpoints from routes.conf files
    val restEndpoints = routesConfFiles.flatMap { file =>
      val content = Files.readString(file.toPath, StandardCharsets.UTF_8)
      RoutesParser.parseRoutes(content).map(e => RestEndpoint(e.method, e.path, e.controller, e.inputParameters))
    }.toSet

    ProjectModel(
      name = repoFolder.getName,
      repository = s"${config.githubUrl}/${config.organization}/${repoFolder.getName}",
      services = allServices,
      dependencies = Set(ProjectDependency(enrichedServiceCalls)),
      restEndpoints = restEndpoints
    )
  }

  private def findFilesWithExtension(baseDir: File, extension: String): Seq[File] = {
    val result = mutable.Buffer[File]()
    val matcher = s".*\\.$extension$$".r

    Files.walkFileTree(
      baseDir.toPath,
      new SimpleFileVisitor[java.nio.file.Path]() {
        override def visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (matcher.findFirstIn(file.toString).isDefined) result += file.toFile
          FileVisitResult.CONTINUE
        }
      }
    )
    result.toSeq
  }

  private def findRoutesConfFiles(baseDir: File): Seq[File] = {
    val result = mutable.Buffer[File]()

    Files.walkFileTree(
      baseDir.toPath,
      new SimpleFileVisitor[java.nio.file.Path]() {
        override def visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (file.getFileName.toString == "routes" || file.getFileName.toString == "routes.conf") {
            result += file.toFile
          }
          FileVisitResult.CONTINUE
        }
      }
    )
    result.toSeq
  }

  private def resolveProjectDependencies(models: Seq[ProjectModel]): Seq[ProjectModel] = {
    logger.info(Cyan("Building service-to-project mapping...").render)
    val serviceToProject = models.flatMap { model =>
      model.services.map(svc => svc.name -> (model.name, model.repository))
    }.toMap
    logger.info(Cyan(s"Mapping completed. Found ${serviceToProject.size} services.").render)

    logger.info(Cyan("Updating project dependencies with project references...").render)
    val updatedModels = models.map { model =>
      val updatedDependencies = model.dependencies.flatMap { dep =>
        val callsByProject: Map[ProjectRef, Set[ServiceCall]] = dep.serviceCalls.groupBy { sc =>
          serviceToProject.get(sc.serviceName) match {
            case Some((pName, pRepo)) =>
              ProjectRef(pName, pRepo)
            case None =>
              ProjectRef() // defaults to NA/NA
          }
        }

        callsByProject.map { case (projectRef, calls) =>
          dep.copy(serviceCalls = calls, project = projectRef)
        }
      }
      model.copy(dependencies = updatedDependencies)
    }

    logger.info(Green("Dependency update completed.").render)
    updatedModels
  }

  private def writeModelsToDisk(
    models: Seq[ProjectModel],
    outputFolder: File,
    appConfig: Option[AppConfig]
  ): Unit = {
    val product = appConfig.map(_.product).getOrElse("ncl")
    val total = models.size
    logger.info(Yellow(s"Writing $total models to disk...").render)

    val pb = new ProgressBarBuilder()
      .setTaskName("Writing Models")
      .setInitialMax(total)
      .setStyle(ProgressBarStyle.UNICODE_BLOCK)
      .build()

    try
      models.foreach { model =>
        val projectOutputFolder = outputFolder.toPath.resolve(product).resolve(model.name).toFile
        if (!projectOutputFolder.exists()) projectOutputFolder.mkdirs()
        val modelOutputPath = projectOutputFolder.toPath.resolve("model.json").toFile
        ProjectExtractorUtil.writeProjectModelToJson(model, modelOutputPath)
        pb.step()
      }
    finally
      pb.close()
  }
}
