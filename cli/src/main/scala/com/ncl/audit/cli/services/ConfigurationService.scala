package com.ncl.audit.cli.services

import com.ncl.audit.cli.model.AppConfig
import com.ncl.audit.cli.model.Config
import com.ncl.audit.cli.model.ConfigError
import com.ncl.audit.cli.model.ParseError
import scopt.OParser
import zio._
import zio.json._

import java.io.File
import scala.io.Source

trait ConfigurationService {
  def loadConfig(cliConfig: Config): Task[AppConfig]
}

final case class ConfigurationServiceImpl() extends ConfigurationService {

  def loadConfig(cliConfig: Config): Task[AppConfig] = for {
    appConfig <- cliConfig.configFile match {
      case Some(file) => parseJsonFile(file)
      case None       => ZIO.fail(ConfigError("Config file path must be provided via --config option."))
    }
    _ <- ZIO.logInfo(s"Loaded ${appConfig.products.size} products from configuration.")
  } yield appConfig

  private def parseJsonFile(file: File): Task[AppConfig] =
    withResource(Source.fromFile(file)) { source =>
      ZIO
        .attemptBlockingIO(source.mkString)
        .flatMap(content => ZIO.fromEither(content.fromJson[AppConfig].left.map(ParseError)))
    }
}

object ConfigurationService {

  val live: ULayer[ConfigurationService] =
    ZLayer.fromFunction(ConfigurationServiceImpl.apply _)

  val builder = OParser.builder[Config]
  val parser = {
    import builder._
    OParser.sequence(
      programName("protobuf-audit-cli"),
      head("protobuf-audit-cli", "1.0"),
      opt[File]('i', "input")
        .valueName("<inputFolder>")
        .action((x, c) => c.copy(inputFolder = Some(x)))
        .text("Input folder containing repositories"),
      opt[File]('o', "output")
        .valueName("<outputFolder>")
        .action((x, c) => c.copy(outputFolder = x))
        .text("Output folder for reports"),
      opt[File]('c', "config")
        .valueName("<configFile>")
        .action((x, c) => c.copy(configFile = Some(x)))
        .text("Configuration file in JSON format"),
      opt[String]('g', "githubUrl")
        .valueName("<githubUrl>")
        .action((x, c) => c.copy(githubUrl = x))
        .text("GitHub URL"),
      opt[String]('r', "organization")
        .valueName("<organization>")
        .action((x, c) => c.copy(organization = x))
        .text("GitHub organization name")
    )
  }

  def parseCliArguments(args: List[String]): Task[Config] =
    ZIO.fromEither(OParser.parse(parser, args, Config()) match {
      case Some(config) => Right(config)
      case None         => Left(ConfigError("Failed to parse command-line arguments."))
    })

  def loadConfig(cliConfig: Config): RIO[ConfigurationService, AppConfig] =
    ZIO.serviceWithZIO[ConfigurationService](_.loadConfig(cliConfig))
}
