package com.ncl.audit.cli

import org.slf4j.LoggerFactory
import scopt.OParser

import java.io.File
import java.nio.file.Paths
import scala.io.Source
import scala.util.Try
import scala.sys.process._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser._

object ProtobufAuditCLI extends App {

  private val logger = LoggerFactory.getLogger(ProtobufAuditCLI.getClass)

  // Configuration case classes
  case class Config(
    inputFolder: Option[File] = None,
    githubUrl: String = "https://github.com",
    organization: String = "norwegian-cruise-line",
    outputFolder: File = new File("output"),
    configFile: Option[File] = None
  )

  case class AppConfig(product: String = "ncl", projects: List[String] = List())

  // Argument parser
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
        .required()
        .valueName("<organization>")
        .action((x, c) => c.copy(organization = x))
        .text("Organization name for cloning repositories."),
      opt[File]('d', "output")
        .optional()
        .valueName("<folder>")
        .action((x, c) => c.copy(outputFolder = x))
        .text("Folder for storing output reports. Defaults to './output'."),
      opt[File]('c', "config")
        .optional()
        .valueName("<file>")
        .action((x, c) => c.copy(configFile = Some(x)))
        .text("Configuration file specifying product and projects."),
      help("help").text("Displays this help message.")
    )
  }

  // Main application logic
  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      // Use fallback input folder if not specified
      val baseFolder = config.inputFolder.getOrElse(new File("/tmp/cloned_repos"))
      val appConfig = config.configFile.map(loadAppConfig).getOrElse(AppConfig())

      logger.info(s"Using base folder: ${baseFolder.getAbsolutePath}")
      logger.info(s"Using output folder: ${config.outputFolder.getAbsolutePath}")

      if (!checkDependencies(Seq("gh", "jq"))) {
        logger.error("Required applications are missing. Please ensure both 'gh' and 'jq' are installed.")
        sys.exit(1)
      }

      cloneRepositories(config.githubUrl, config.organization, baseFolder, appConfig)
      processRepositories(config, baseFolder, config.outputFolder, appConfig)

    case _ =>
      logger.error("Invalid arguments or help requested.")
      sys.exit(1)
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

    decode[AppConfig](content) match {
      case Right(config) =>
        logger.info(s"Loaded AppConfig: $config")
        config
      case Left(error) =>
        logger.error(s"Failed to parse config file: ${error.getMessage}")
        logger.error(s"Content: $content")
        sys.exit(1)
    }
  }

  private def cloneRepositories(
    githubUrl: String,
    organization: String,
    baseFolder: File,
    appConfig: AppConfig
  ): Unit = {
    if (!baseFolder.exists()) baseFolder.mkdirs()

    val repos = if (appConfig.projects.nonEmpty) {
      appConfig.projects
    } else {
      logger.warn("No projects specified in config file. Fetching all repositories using GitHub CLI.")
      s"gh repo list $organization --json name | jq -r '.[].name'".!!.trim.split("\n").toSeq
    }

    repos.foreach { repo =>
      val repoPath = Paths.get(baseFolder.getAbsolutePath, repo).toFile
      if (!repoPath.exists()) {
        val cloneCommand = s"git clone $githubUrl/$organization/$repo.git ${repoPath.getAbsolutePath}"
        logger.info(s"Running clone command: $cloneCommand")
        val result = cloneCommand.!
        if (result != 0) {
          logger.error(s"Failed to clone repository: $repo")
        }
      } else {
        logger.info(s"Repository already exists: ${repoPath.getAbsolutePath}")
      }
    }
  }

  private def processRepositories(
    config: Config,
    inputFolder: File,
    outputFolder: File,
    appConfig: AppConfig
  ): Unit = {
    val productFolder = outputFolder.toPath.resolve(appConfig.product).toFile
    if (!productFolder.exists()) productFolder.mkdirs()

    appConfig.projects.foreach { project =>
      val repoFolder = new File(inputFolder, project)
      val projectOutputFolder = new File(productFolder, project)

      if (repoFolder.exists() && repoFolder.isDirectory) {
        if (!projectOutputFolder.exists()) projectOutputFolder.mkdirs()
        logger.info(s"Processing repository: ${repoFolder.getName}")
        // Add repository-specific processing logic here
      } else {
        logger.warn(s"Repository folder not found: ${repoFolder.getAbsolutePath}")
      }
    }
  }
}
