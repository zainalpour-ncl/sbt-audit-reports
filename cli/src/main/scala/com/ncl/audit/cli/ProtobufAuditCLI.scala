package com.ncl.audit.cli

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
import scala.sys.process._

object ProtobufAuditCLI extends App {

  private val logger = LoggerFactory.getLogger(ProtobufAuditCLI.getClass)

  case class Config(
    inputFolder: Option[File] = None,
    githubUrl: String = "",
    organization: String = "",
    outputFolder: File = new File("output")
  )

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
        .text("Folder containing the cloned repositories."),
      opt[String]('g', "githubUrl")
        .required()
        .valueName("<url>")
        .action((x, c) => c.copy(githubUrl = x))
        .text("GitHub server URL."),
      opt[String]('o', "organization")
        .required()
        .valueName("<organization>")
        .action((x, c) => c.copy(organization = x))
        .text("Organization name for cloning repositories."),
      opt[File]('d', "output")
        .optional()
        .valueName("<folder>")
        .action((x, c) => c.copy(outputFolder = x))
        .text("Folder for storing output reports.")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      if (!checkDependencies(Seq("gh", "jq"))) {
        logger.error("Required applications are missing. Please ensure both 'gh' and 'jq' are installed.")
        sys.exit(1)
      }
      val baseFolder = config.inputFolder.getOrElse(new File("cloned_repos"))
      cloneRepositories(config.githubUrl, config.organization, baseFolder)
      processRepositories(config, baseFolder, config.outputFolder)
    case _ =>
      logger.error("Invalid arguments or help requested.")
  }

  private def checkDependencies(dependencies: Seq[String]): Boolean =
    dependencies.forall { app =>
      val result = s"which $app".!
      if (result != 0) logger.error(s"Application '$app' is not installed or not found in PATH.")
      result == 0
    }

  private def cloneRepositories(githubUrl: String, organization: String, baseFolder: File): Unit = {
    if (!baseFolder.exists()) baseFolder.mkdirs()
    val repos = s"gh repo list $organization --json name | jq -r '.[].name'".!!.trim.split("\n")
    repos.foreach { repo =>
      val repoPath = Paths.get(baseFolder.getAbsolutePath, repo).toFile
      if (!repoPath.exists()) {
        val cloneCommand = s"git clone $githubUrl/$organization/$repo.git ${repoPath.getAbsolutePath}"
        logger.info(s"Cloning repository: $repo")
        cloneCommand.!
      }
    }
  }

  private def processRepositories(config: Config, inputFolder: File, outputFolder: File): Unit = {
    if (!outputFolder.exists()) outputFolder.mkdirs()

    inputFolder.listFiles().filter(_.isDirectory).foreach { repoFolder =>
      logger.info(s"Processing repository: ${repoFolder.getName}")
      val protoFiles = findFilesWithExtension(repoFolder, "proto")
      val scalaFiles = findFilesWithExtension(repoFolder, "scala")

      val allServices = protoFiles.flatMap(file => ProtobufParserUtil.parseFile(file.toString))
      val enrichedServiceCalls = scalaFiles.flatMap { file =>
        val sourceCode = Files.readString(file.toPath, StandardCharsets.UTF_8)
        InjectedServiceAnalyzer.analyzeServiceCalls(sourceCode, file.getName)
      }

      val projectModel = ProjectModel(
        name = repoFolder.getName,
        repository = s"${config.githubUrl}/${config.organization}/${repoFolder.getName}",
        services = allServices.toSet,
        dependencies = Set(ProjectDependency(enrichedServiceCalls.toSet))
      )

      // Write Reports
      val modelOutputPath = outputFolder.toPath.resolve(s"${repoFolder.getName}_model.json").toFile
      ProjectExtractorUtil.writeProjectModelToJson(projectModel, modelOutputPath)
    }
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
    result
  }
}
