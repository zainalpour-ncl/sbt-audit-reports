package com.ncl.audit.cli

import com.ncl.audit.RoutesParser
import com.ncl.audit._
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueType
import com.typesafe.config.{Config => TypesafeConfig}

import fansi.Color._
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.slf4j.LoggerFactory
import scopt.OParser

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Collections
import scala.collection.mutable
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.sys.process._
import scala.util.Try
import scala.util.Using

object ProtobufAuditCLI extends App {

  private val logger = LoggerFactory.getLogger(ProtobufAuditCLI.getClass)

  case class Config(
    inputFolder: Option[File] = None,
    githubUrl: String = "https://github.com",
    organization: String = "norwegian-cruise-line",
    outputFolder: File = new File("output"),
    configFile: Option[File] = None
  )

  case class ProductConfig(product: String, projects: List[String])
  case class AppConfig(products: List[ProductConfig])

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
        .text("Configuration file specifying products and projects.")
    )
  }

  OParser.parse(parser, args, Config()) match {
    case Some(config) =>
      if (!checkDependencies(Seq("gh", "jq"))) {
        logger.error(Red("Required applications 'gh' and 'jq' are missing.").render)
        sys.exit(1)
      }

      logger.info(Cyan("Loading configuration...").render)
      val appConfig = config.configFile.map(loadAppConfig)
      val baseFolder = config.inputFolder.getOrElse(new File("/tmp/cloned_repos"))

      logger.info(Magenta("Cloning repositories if necessary...").render)
      cloneRepositories(config.githubUrl, config.organization, baseFolder, appConfig)
      logger.info(Green("Cloning complete.").render)

      logger.info(Magenta("Processing repositories...").render)
      val projectModels = processRepositories(config, baseFolder, appConfig)
      logger.info(Green("Repository processing complete.").render)

      logger.info(Magenta("Resolving project dependencies...").render)
      val resolvedModels = resolveProjectDependencies(projectModels)
      logger.info(Green("Dependency resolution complete.").render)

      logger.info(Magenta("Writing reports to disk...").render)
      writeModelsToDisk(resolvedModels, config.outputFolder)
      writeReports(resolvedModels, config.outputFolder)
      logger.info(Green("All reports have been successfully written.").render)

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
  ): Unit = {
    if (!baseFolder.exists()) baseFolder.mkdirs()

    val repos = appConfig match {
      case Some(AppConfig(products)) =>
        products.flatMap(_.projects)
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
  }

  private def processRepositories(
    config: Config,
    inputFolder: File,
    appConfig: Option[AppConfig]
  ): Seq[ProjectModel] = {
    val productProjectTuples = appConfig match {
      case Some(AppConfig(products)) =>
        products.flatMap(p => p.projects.map(proj => (p.product, proj)))
      case None =>
        val dirs = inputFolder.listFiles().filter(_.isDirectory).map(d => ("ncl", d.getName)).toSeq
        dirs
    }

    if (productProjectTuples.nonEmpty) {
      logger.info(Yellow(s"Processing ${productProjectTuples.size} repositories...").render)
    } else {
      logger.warn(Yellow("No repositories found to process.").render)
    }

    val pb = new ProgressBarBuilder()
      .setTaskName("Processing Repos")
      .setInitialMax(productProjectTuples.size)
      .setStyle(ProgressBarStyle.UNICODE_BLOCK)
      .build()

    val models =
      try
        productProjectTuples.flatMap { case (product, projectName) =>
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
    val samlConfFiles = findSamlConfFiles(repoFolder)

    // gRPC services
    val allServices = protoFiles.flatMap(file => ProtobufParserUtil.parseFile(file.toString)).toSet

    // service calls
    val enrichedServiceCalls = scalaFiles.flatMap { file =>
      val sourceCode = Files.readString(file.toPath, StandardCharsets.UTF_8)
      InjectedServiceAnalyzer.analyzeServiceCalls(sourceCode, file.getName)
    }.toSet

    // REST endpoints
    val restEndpoints = routesConfFiles.flatMap { file =>
      val content = Files.readString(file.toPath, StandardCharsets.UTF_8)
      RoutesParser.parseRoutes(content).map(e => RestEndpoint(e.method, e.path, e.controller, e.inputParameters))
    }.toSet

    // SAML configurations
    val samlConfigurations = samlConfFiles.flatMap(parseSamlConfiguration).toSet

    ProjectModel(
      name = repoFolder.getName,
      repository = s"${config.githubUrl}/${config.organization}/${repoFolder.getName}",
      product = product,
      services = allServices,
      dependencies = Set(ProjectDependency(enrichedServiceCalls)),
      restEndpoints = restEndpoints,
      samlConfigurations = samlConfigurations
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
          val name = file.getFileName.toString
          if ((name == "routes" || name == "routes.conf") && !file.toString.contains("test")) {
            result += file.toFile
          }
          FileVisitResult.CONTINUE
        }
      }
    )
    result.toSeq
  }

  private def findSamlConfFiles(baseDir: File): Seq[File] = {
    val result = mutable.Buffer[File]()
    Files.walkFileTree(
      baseDir.toPath,
      new SimpleFileVisitor[java.nio.file.Path]() {
        override def visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult = {
          val name = file.getFileName.toString
          val pathString = file.toString
          if (name.endsWith(".conf") && !pathString.contains("test")) {
            result += file.toFile
          }
          FileVisitResult.CONTINUE
        }
      }
    )
    result
  }

  private def parseSamlConfiguration(confFile: File): Option[SamlConfiguration] = {
    val defaults = ConfigFactory
      .empty()
      .withValue("APPLICATION_NAME", ConfigValueFactory.fromAnyRef("NA"))
      .withValue(
        "cinnamon.jmx-importer.beans",
        ConfigValueFactory.fromAnyRef(Collections.emptyList())
      )
      .withValue("play.http.fileMimeTypes", ConfigValueFactory.fromAnyRef("default_mime_types"))

    val config = ConfigFactory
      .parseFile(confFile)
      .withFallback(defaults)
      .resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))

    if (config.hasPath("saml")) {
      val samlConf = config.getConfig("saml")
      if (samlConf.hasPath("roles") && samlConf.hasPath("profile-groups-attribute")) {
        val profileGroupsAttribute = samlConf.getString("profile-groups-attribute")
        val rolesConfig = samlConf.getConfig("roles")

        val roles = rolesConfig
          .entrySet()
          .asScala
          .filter(_.getValue.valueType() == ConfigValueType.LIST)
          .map { entry =>
            val roleName = entry.getKey
            val groups = rolesConfig.getStringList(roleName).asScala.toSeq
            roleName -> groups
          }
          .toMap

        val definedIn = confFile.getPath
        Some(SamlConfiguration(profileGroupsAttribute, roles, definedIn))
      } else {
        None
      }
    } else {
      None
    }
  }

  private def resolveProjectDependencies(models: Seq[ProjectModel]): Seq[ProjectModel] = {
    logger.info(fansi.Color.Cyan("Building service-to-project mapping...").render)

    // Map: serviceName -> (projectName, projectRepository, product)
    val serviceToProject = models.flatMap { model =>
      model.services.map(svc => svc.name -> (model.name, model.repository, model.product))
    }.toMap

    logger.info(fansi.Color.Cyan(s"Mapping completed. Found ${serviceToProject.size} services.").render)

    logger.info(fansi.Color.Cyan("Updating project dependencies with project references...").render)

    val updatedModels = models.map { model =>
      val updatedDependencies = model.dependencies.flatMap { dep =>
        val callsByProject: Map[ProjectRef, Set[ServiceCall]] = dep.serviceCalls.groupBy { sc =>
          serviceToProject.get(sc.serviceName) match {
            case Some((pName, pRepo, pProduct)) =>
              ProjectRef(pName, pRepo, pProduct)
            case None =>
              ProjectRef()
          }
        }

        callsByProject.map { case (projectRef, calls) =>
          dep.copy(serviceCalls = calls, project = projectRef)
        }
      }
      model.copy(dependencies = updatedDependencies)
    }

    logger.info(fansi.Color.Green("Dependency update completed.").render)
    updatedModels
  }

  private def writeModelsToDisk(
    models: Seq[ProjectModel],
    outputFolder: File
  ): Unit = {
    val total = models.size
    logger.info(Yellow(s"Writing $total models to disk...").render)

    val pb = new ProgressBarBuilder()
      .setTaskName("Writing Models")
      .setInitialMax(total)
      .setStyle(ProgressBarStyle.UNICODE_BLOCK)
      .build()

    try
      models.foreach { model =>
        val projectOutputFolder = outputFolder.toPath.resolve(model.product).resolve(model.name).toFile
        if (!projectOutputFolder.exists()) projectOutputFolder.mkdirs()
        val modelOutputPath = projectOutputFolder.toPath.resolve("model.json").toFile
        ProjectExtractorUtil.writeProjectModelToJson(model, modelOutputPath)
        pb.step()
      }
    finally
      pb.close()
  }

  private def writeLine(out: PrintWriter, line: String): Unit = {
    out.write(line)
    out.write("\n")
  }

  /**
   * Writes CSV reports for each project model into its respective product/project directory. Each project's directory
   * will contain:
   *   - hosted_services-REST.csv
   *   - hosted_services-gRPC.csv
   *   - Dependent_services.csv
   *   - Permission-role_matrix.csv
   */
  def writeReports(models: Seq[ProjectModel], outputFolder: File): Unit = {
    logger.info(Magenta("Generating per-project CSV reports...").render)

    for (model <- models) {
      // product and project directories already exist due to writeModelsToDisk
      val projectOutputFolder = outputFolder.toPath.resolve(model.product).resolve(model.name).toFile
      if (!projectOutputFolder.exists()) projectOutputFolder.mkdirs()

      // hosted_services-REST.csv
      val restFile = new File(projectOutputFolder, "hosted_services-REST.csv")
      logger.info(
        Cyan(s"Writing REST endpoints report for ${model.product}/${model.name}: ${restFile.getAbsolutePath}").render
      )
      Using(new PrintWriter(new FileWriter(restFile))) { out =>
        writeLine(out, "product,service,method,endpoint,arguments,response,implemented_by")
        for (endpoint <- model.restEndpoints) {
          val product = model.product
          val service = model.name
          val method = endpoint.method
          val endpointPath = endpoint.path
          val arguments = endpoint.inputParameters.getOrElse("")
          val response = "" // TODO: response schema not captured
          val implementedBy = endpoint.controller
          // TODO: permissions, depends_on
          writeLine(out, s"$product,$service,$method,$endpointPath,$arguments,$response,$implementedBy")
        }
      }.recover { case e: Exception =>
        logger.error(Red(s"Failed to write REST endpoints report for ${model.name}: ${e.getMessage}").render)
      }

      // hosted_services-gRPC.csv
      val grpcFile = new File(projectOutputFolder, "hosted_services-gRPC.csv")
      logger.info(
        Cyan(s"Writing gRPC endpoints report for ${model.product}/${model.name}: ${grpcFile.getAbsolutePath}").render
      )
      Using(new PrintWriter(new FileWriter(grpcFile))) { out =>
        writeLine(out, "product,service,Endpoint,Argument,Response,DefinedIn,ImplementedIn")
        for (svc <- model.services; method <- svc.methods) {
          val product = model.product
          val service = model.name
          val endpoint = method.name
          val argument = method.inputType
          val response = method.outputType
          val definedIn = svc.definedIn.getOrElse("")
          val implementedIn = "" // TODO: determine implementedIn
          // TODO: PublishesTo/WritesTo, ConsumesFrom/ReadsFrom
          writeLine(out, s"$product,$service,$endpoint,$argument,$response,$definedIn,$implementedIn")
        }
      }.recover { case e: Exception =>
        logger.error(Red(s"Failed to write gRPC endpoints report for ${model.name}: ${e.getMessage}").render)
      }

      // Dependent_services.csv
      val dependentFile = new File(projectOutputFolder, "Dependent_services.csv")
      logger.info(
        Cyan(
          s"Writing dependent services report for ${model.product}/${model.name}: ${dependentFile.getAbsolutePath}"
        ).render
      )
      Using(new PrintWriter(new FileWriter(dependentFile))) { out =>
        writeLine(out, "Product,Service,Service_name,CallsTo,Endpoint,Argument,Response")
        for (dep <- model.dependencies; sc <- dep.serviceCalls; m <- sc.calledMethods) {
          val product = model.product
          val service = model.name
          val serviceName = sc.serviceName
          val callsTo = dep.project.name
          val endpoint = m.methodName
          val argument = m.inputType.getOrElse("")
          val response = m.outputType.getOrElse("")
          writeLine(out, s"$product,$service,$serviceName,$callsTo,$endpoint,$argument,$response")
        }
      }.recover { case e: Exception =>
        logger.error(Red(s"Failed to write dependent services report for ${model.name}: ${e.getMessage}").render)
      }

      // Permission-role_matrix.csv
      // For this project only, collect roles and groups from samlConfigurations.
      val permissionFile = new File(projectOutputFolder, "Permission-role_matrix.csv")
      logger.info(
        Cyan(
          s"Writing permission-role matrix for ${model.product}/${model.name}: ${permissionFile.getAbsolutePath}"
        ).render
      )
      val allRoles = mutable.Set[String]()
      val allGroups = mutable.Set[String]()

      for (saml <- model.samlConfigurations; (role, groups) <- saml.roles) {
        allRoles += role
        allGroups ++= groups
      }

      val sortedRoles = allRoles.toSeq.sorted
      val sortedGroups = allGroups.toSeq.sorted

      Using(new PrintWriter(new FileWriter(permissionFile))) { out =>
        writeLine(out, ("permission" +: sortedRoles).mkString(","))
        for (group <- sortedGroups) {
          val row = sortedRoles.map { role =>
            val hasGroup = model.samlConfigurations.exists(saml => saml.roles.get(role).exists(_.contains(group)))
            if (hasGroup) "true" else "false"
          }
          writeLine(out, (group +: row).mkString(","))
        }
      }.recover { case e: Exception =>
        logger.error(Red(s"Failed to write permission-role matrix for ${model.name}: ${e.getMessage}").render)
      }

      logger.info(Green(s"CSV reports generated for ${model.product}/${model.name}.").render)
    }

    logger.info(Green("All per-project CSV reports generated successfully.").render)
  }
}
