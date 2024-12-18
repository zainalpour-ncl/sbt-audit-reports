package com.ncl.audit.cli.model

import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder

import java.io.File

final case class Config(
  inputFolder: Option[File] = None,
  githubUrl: String = "https://github.com",
  organization: String = "norwegian-cruise-line",
  outputFolder: File = new File("output"),
  configFile: Option[File] = None
)

final case class ProductConfig(product: String, projects: List[String])
final case class AppConfig(products: List[ProductConfig])

object AppConfig {
  implicit val productConfigDecoder: JsonDecoder[ProductConfig] = DeriveJsonDecoder.gen[ProductConfig]
  implicit val appConfigDecoder: JsonDecoder[AppConfig] = DeriveJsonDecoder.gen[AppConfig]
}

sealed trait ConfigServiceError extends Throwable

case class ConfigError(message: String) extends ConfigServiceError {
  override def getMessage: String = message
}
case class ParseError(message: String) extends ConfigServiceError {
  override def getMessage: String = message
}
