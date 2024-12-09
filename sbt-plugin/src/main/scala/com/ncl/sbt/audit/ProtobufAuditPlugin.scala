package com.ncl.sbt.audit

import com.ncl.audit.*
import sbt.*
import sbt.Keys.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

object ProtobufAuditPlugin extends AutoPlugin {

  object autoImport {
    val auditProtobufFiles = taskKey[Unit]("Generate audit report for Protobuf files")
    val protobufSourcePath = settingKey[Option[File]]("Optional path to the directory containing Protobuf files")
    val scalaSourcePath = settingKey[File]("Path to the directory containing Scala source files")
    val auditReportOutputPath = settingKey[File]("Path to output the generated audit report (default: target folder)")
    val projectModelOutputPath = settingKey[File]("Path to output the project model JSON")
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[_]] = Seq(
    protobufSourcePath := None,
    scalaSourcePath := (Compile / sourceDirectory).value / "scala",
    auditReportOutputPath := (Compile / target).value / "protobuf-audit.csv",
    projectModelOutputPath := (Compile / target).value / "project-model.json",
    auditProtobufFiles := {
      val log = streams.value.log
      val baseDir = baseDirectory.value
      val protoPath = protobufSourcePath.value.getOrElse(baseDir)
      // val scalaPath = scalaSourcePath.value
      val scalaPath = baseDirectory.value
      val outputPath = auditReportOutputPath.value
      val modelOutputPath = projectModelOutputPath.value

      log.info("Starting Protobuf audit...")

      // Extract project metadata
      val projectName = ProjectExtractorUtil.extractProjectName(baseDir)
      val repository = ProjectExtractorUtil.extractRepository(baseDir)
      log.info(s"Project name: $projectName")
      log.info(s"Repository: $repository")

      // Parse Protobuf files
      val protoFiles = (protoPath ** "*.proto").get()
      val allServices = protoFiles.flatMap { file =>
        log.debug(s"Processing Protobuf file: ${file.getAbsolutePath}")
        try ProtobufParserUtil.parseFile(file.getAbsolutePath)
        catch {
          case e: Exception =>
            throw new MessageOnlyException( // scalastyle:ignore
              s"Error processing file ${file.getAbsolutePath}: ${e.getMessage}"
            )
        }
      }.toSet

      val externalServices =
        allServices.filter(_.definedIn.exists(path => path.contains("protobuf_external") || path.contains("target")))

      val internalServices = allServices.diff(externalServices)

      val methods = allServices.flatMap(_.methods)

      // Analyze Scala source files for ServiceCalls
      log.info(s"Analyzing Scala source files in $scalaPath")
      val scalaFiles = (scalaPath ** "*.scala").get()
      val serviceCalls = scalaFiles.flatMap { file =>
        log.debug(s"Processing Scala file: ${file.getAbsolutePath}")
        val sourceCode = Files.readString(file.toPath, StandardCharsets.UTF_8)
        try InjectedServiceAnalyzer.analyzeServiceCalls(sourceCode, file.getName)
        catch {
          case e: Exception =>
            log.error(s"Error processing Scala file ${file.getAbsolutePath}: ${e.getMessage}")
            Seq.empty
        }
      }.toSet

      // Enrich ServiceCalls with input and output types from externalServices
      val enrichedServiceCalls = serviceCalls.map { call =>
        val enrichedMethods = call.calledMethods.map { method =>
          methods.find(_.name == method.methodName) match {
            case Some(rpcMethod) =>
              method.copy(inputType = Some(rpcMethod.inputType), outputType = Some(rpcMethod.outputType))
            case None => method
          }
        }
        call.copy(calledMethods = enrichedMethods)
      }

      // Update ProjectModel
      val projectModel = ProjectModel(
        name = projectName,
        repository = repository,
        services = internalServices,
        dependencies = Set(ProjectDependency(enrichedServiceCalls)),
        product = "NA"
      )

      // Write ProjectModel to JSON
      log.info(s"Writing project model to ${modelOutputPath.getAbsolutePath}")
      ProjectExtractorUtil.writeProjectModelToJson(projectModel, modelOutputPath)

      // Write Service data to CSV for Protobuf audit
      val serviceData = allServices.flatMap { service =>
        service.methods.map { method =>
          Seq(service.name, method.name, method.inputType, method.outputType).mkString(",")
        }
      }.toSeq

      if (serviceData.nonEmpty) {
        log.info(s"Writing results to ${outputPath.getAbsolutePath}")
        val header = "Service,Method,InputType,OutputType"
        val csvContent = (header +: serviceData).mkString("\n")
        Files.write(Paths.get(outputPath.getAbsolutePath), csvContent.getBytes(StandardCharsets.UTF_8))
        log.info("Protobuf audit completed successfully.")
      } else {
        throw new MessageOnlyException("No services found in the Protobuf files.") // scalastyle:ignore
      }
    }
  )
}
