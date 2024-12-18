package com.ncl.audit.cli.services

import com.ncl.audit.SamlConfiguration
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigValueType
import zio._

import java.io.File
import scala.jdk.CollectionConverters._

trait SamlConfigurationService {
  def parseSamlConfigs(samlFiles: List[File]): Task[Set[SamlConfiguration]]
}

final case class SamlConfigurationServiceImpl() extends SamlConfigurationService {

  override def parseSamlConfigs(samlFiles: List[File]): Task[Set[SamlConfiguration]] =
    ZIO.foreachPar(samlFiles)(parseSingleSamlFile).map(_.flatten.toSet)

  private def parseSingleSamlFile(file: File): Task[Option[SamlConfiguration]] =
    ZIO
      .attemptBlocking {
        val config = ConfigFactory
          .parseFile(file)
          .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))

        extractSamlConfiguration(config, file)
      }
      .mapError(e => new Exception(s"Failed to parse SAML file: ${file.getAbsolutePath}", e))

  private def extractSamlConfiguration(config: Config, file: File): Option[SamlConfiguration] =
    if (config.hasPath("saml")) {
      val samlConfig = config.getConfig("saml")

      for {
        profileGroupsAttribute <- extractProfileGroupsAttribute(samlConfig, file)
        roles = extractRoles(samlConfig)
      } yield SamlConfiguration(
        profileGroupsAttribute = profileGroupsAttribute,
        roles = roles,
        definedIn = file.getAbsolutePath
      )
    } else {
      None
    }

  private def extractProfileGroupsAttribute(samlConfig: Config, file: File): Option[String] =
    if (samlConfig.hasPath("profile-groups-attribute"))
      Some(samlConfig.getString("profile-groups-attribute"))
    else
      None

  private def extractRoles(samlConfig: Config): Map[String, Seq[String]] =
    if (samlConfig.hasPath("roles")) {
      val rolesConfig = samlConfig.getConfig("roles")
      rolesConfig
        .entrySet()
        .asScala
        .collect {
          // scalastyle:off
          case entry if entry.getValue.valueType() == ConfigValueType.LIST =>
            val roleName = entry.getKey
            val groups = rolesConfig.getStringList(roleName).asScala.toSeq
            roleName -> groups
        }
        .toMap
    } else {
      Map.empty[String, Seq[String]]
    }
}

object SamlConfigurationService {
  val live: ULayer[SamlConfigurationService] = ZLayer.fromFunction(SamlConfigurationServiceImpl.apply _)

  def parseSamlConfigs(samlFiles: List[File]): RIO[SamlConfigurationService, Set[SamlConfiguration]] =
    ZIO.serviceWithZIO[SamlConfigurationService](_.parseSamlConfigs(samlFiles))
}
