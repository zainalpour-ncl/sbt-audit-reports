package com.ncl.sbt.audit

import com.ncl.audit.ProtobufParserUtil
import sbt.*
import sbt.Keys.*

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

object ProtobufAuditPlugin extends AutoPlugin {

  object autoImport {
    val auditProtobufFiles = taskKey[Unit]("Generate audit report for Protobuf files")
    val protobufSourcePath = settingKey[Option[File]]("Optional path to the directory containing Protobuf files")
    val auditReportOutputPath = settingKey[File]("Path to output the generated audit report (default: target folder)")
  }

  import autoImport.*

  override def projectSettings: Seq[Setting[_]] = Seq(
    protobufSourcePath := None,
    auditReportOutputPath := (Compile / target).value / "protobuf-audit.csv",

    auditProtobufFiles := {
      val log = streams.value.log
      val protoPath = protobufSourcePath.value.getOrElse(baseDirectory.value)
      val outputPath = auditReportOutputPath.value

      log.info(s"Starting Protobuf audit...")
      log.info(s"Protobuf source path: ${protoPath.getAbsolutePath}")
      log.info(s"Audit report output path: ${outputPath.getAbsolutePath}")

      // Check if the source path exists
      if (!protoPath.exists()) {
        throw new MessageOnlyException(s"Specified source path does not exist: ${protoPath.getAbsolutePath}")
      }

      // Recursively find all .proto files
      val protoFiles = (protoPath ** "*.proto").get
      log.info(s"Found ${protoFiles.size} Protobuf files to process.")

      if (protoFiles.isEmpty) {
        throw new MessageOnlyException("No Protobuf files found.")
      }

      // Process each file and collect results
      val serviceData = protoFiles.flatMap { file =>
        log.info(s"Processing file: ${file.getAbsolutePath}")
        try {
          ProtobufParserUtil.parseFile(file.getAbsolutePath).flatMap { service =>
            service.methods.map { method =>
              Seq(
                file.getAbsolutePath,
                service.name,
                method.name,
                method.inputType,
                method.outputType
              ).mkString(",") // Convert to CSV format
            }
          } // Flatten nested lists
        } catch {
          case e: Exception =>
            throw new MessageOnlyException(s"Error processing file ${file.getAbsolutePath}: ${e.getMessage}")
        }
      }

      // Write results to CSV
      if (serviceData.nonEmpty) {
        log.info(s"Writing results to ${outputPath.getAbsolutePath}")
        val header = "File,Service,Method,InputType,OutputType"
        val csvContent = (header +: serviceData).mkString("\n")
        Files.write(Paths.get(outputPath.getAbsolutePath), csvContent.getBytes(StandardCharsets.UTF_8))
        log.info("Protobuf audit completed successfully.")
      } else {
        throw new MessageOnlyException("No services found in the Protobuf files.")
      }
    }
  )
}
