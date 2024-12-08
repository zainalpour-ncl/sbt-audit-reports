package com.ncl.audit.cli

import com.ncl.audit.RoutesParser
import com.ncl.audit._
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
        logger.error("Required applications are missing. Please ensure both 'gh' and 'jq' are installed.")
        sys.exit(1)
      }

      val appConfig = config.configFile.map(loadAppConfig)
      val baseFolder = config.inputFolder.getOrElse(new File("/tmp/cloned_repos"))
      cloneRepositories(config.githubUrl, config.organization, baseFolder, appConfig)
      processRepositories(config, baseFolder, config.outputFolder, appConfig)

    case _ =>
      logger.error("Invalid arguments or help requested.")
  }

  private def checkDependencies(dependencies: Seq[String]): Boolean =
    dependencies.forall { app =>
      val result = s"which $app".!
      if (result != 0) logger.error(s"Application '$app' is not installed or not found in PATH.")
      result == 0
    }

  private def loadAppConfig(file: File): AppConfig = {
    val content = Try(Source.fromFile(file).mkString).getOrElse {
      logger.error(s"Failed to read config file: ${file.getAbsolutePath}")
      sys.exit(1)
    }
    import io.circe.generic.auto._
    import io.circe.parser._
    decode[AppConfig](content) match {
      case Right(config) => config
      case Left(error) =>
        logger.error(s"Failed to parse config file: $error")
        sys.exit(1)
    }
  }

  private def cloneRepositories(
    githubUrl: String,
    organization: String,
    baseFolder: File,
    appConfig: Option[AppConfig]
  ): Unit = {
    if (!baseFolder.exists()) baseFolder.mkdirs()

    val repos = appConfig match {
      case Some(config) => config.projects
      case None         => s"gh repo list $organization --json name | jq -r '.[].name'".!!.trim.split("\n").toSeq
    }

    repos.foreach { repo =>
      val repoPath = Paths.get(baseFolder.getAbsolutePath, repo).toFile
      if (!repoPath.exists()) {
        val cloneCommand = s"git clone $githubUrl/$organization/$repo.git ${repoPath.getAbsolutePath}"
        logger.info(s"Cloning repository: $repo")
        cloneCommand.!
      }
    }
  }

  private def processRepositories(
    config: Config,
    inputFolder: File,
    outputFolder: File,
    appConfig: Option[AppConfig]
  ): Unit =
    appConfig match {
      case Some(AppConfig(product, projects)) =>
        projects.foreach { project =>
          val repoFolder = new File(inputFolder, project)
          if (repoFolder.exists() && repoFolder.isDirectory) {
            val projectOutputFolder = outputFolder.toPath.resolve(product).resolve(project).toFile
            processSingleRepository(repoFolder, config, projectOutputFolder)
          }
        }
      case None =>
        inputFolder
          .listFiles()
          .filter(_.isDirectory)
          .foreach { repoFolder =>
            val projectOutputFolder = outputFolder.toPath.resolve(repoFolder.getName).toFile
            processSingleRepository(repoFolder, config, projectOutputFolder)
          }
    }

  private def processSingleRepository(repoFolder: File, config: Config, projectOutputFolder: File): Unit = {
    if (!projectOutputFolder.exists()) projectOutputFolder.mkdirs()
    logger.info(s"Processing repository: ${repoFolder.getName}")

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

    val projectModel = ProjectModel(
      name = repoFolder.getName,
      repository = s"${config.githubUrl}/${config.organization}/${repoFolder.getName}",
      services = allServices,
      dependencies = Set(ProjectDependency(enrichedServiceCalls)),
      restEndpoints = restEndpoints
    )

    val modelOutputPath = projectOutputFolder.toPath.resolve("model.json").toFile
    ProjectExtractorUtil.writeProjectModelToJson(projectModel, modelOutputPath)
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
}
